package com.mockhub.mock.service.match;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.dto.match.MatchCondition;
import com.mockhub.mock.model.dto.match.MatchRule;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 条件响应匹配引擎。
 * <p>
 * 给定接口 ID 和原始请求，返回应当使用的 ApiResponse：
 * <ol>
 *   <li>启用 Tab（is_active=1）数量为 0 → 返回 null（上游兜底 500）</li>
 *   <li>启用 Tab 数量为 1 → 直接返回唯一启用项（短路，等价历史单响应体行为）</li>
 *   <li>启用 Tab 数量 &ge; 2 → 按 sort_order 顺序遍历"有规则"的项，
 *       第一条命中返回；都不命中返回唯一"无规则"项作为兜底</li>
 * </ol>
 * <p>
 * 操作符与语义见 {@link Operators}。所有运行时错误（JSON 乱码、正则编译失败、
 * 数字解析失败）一律判条件不满足并写 warn 日志，绝不抛异常到调用方。
 */
@Service
public class ResponseMatcher {

    private static final Logger log = LoggerFactory.getLogger(ResponseMatcher.class);

    /** Request attribute key：缓存原始 body 字符串，由 MockDispatchService 在分发前写入 */
    public static final String ATTR_RAW_BODY = "__mockhub_raw_body__";
    /** Request attribute key：缓存解析后的 JsonNode（本类内写入，供同请求多次条件复用） */
    public static final String ATTR_PARSED_BODY = "__mockhub_parsed_body__";

    private final ApiResponseRepository apiResponseRepository;
    private final ObjectMapper objectMapper;

    public ResponseMatcher(ApiResponseRepository apiResponseRepository,
                           ObjectMapper objectMapper) {
        this.apiResponseRepository = apiResponseRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 为请求挑选命中的 ApiResponse。
     *
     * @param apiId 接口 ID
     * @param req   原始请求
     * @return 命中的返回体；启用数为 0 时返回 null，由调用方决定如何兜底
     */
    public ApiResponse match(String apiId, HttpServletRequest req) {
        List<ApiResponse> enabled = apiResponseRepository.findEnabledByApiId(apiId);

        if (enabled.isEmpty()) {
            log.warn("接口 {} 无启用返回体", apiId);
            return null;
        }

        // 单启用短路：等同历史单响应体模式
        if (enabled.size() == 1) {
            return enabled.get(0);
        }

        // 多启用：按 sort_order 遍历"有规则"项
        ApiResponse fallback = null;
        for (ApiResponse resp : enabled) {
            MatchRule rule = parseRule(resp.getConditions());
            if (rule == null || rule.isEmpty()) {
                // 无规则项 = 兜底候选；保留第一个遇到的（保存校验保证恰好一个）
                if (fallback == null) {
                    fallback = resp;
                }
                continue;
            }
            if (matchRule(rule, req)) {
                log.debug("接口 {} 命中返回体 {}（{}）", apiId, resp.getId(), resp.getName());
                return resp;
            }
        }

        if (fallback != null) {
            log.debug("接口 {} 所有规则均未命中，走兜底返回体 {}", apiId, fallback.getId());
            return fallback;
        }

        // 理论上保存校验已拦截（启用 ≥ 2 必有一个兜底）；运行时兜底保护
        log.warn("接口 {} 启用 {} 条返回体但无兜底（数据异常）", apiId, enabled.size());
        return null;
    }

    /**
     * 反序列化 conditions JSON 为 MatchRule。失败返回 null（视为无规则）。
     */
    private MatchRule parseRule(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(conditionsJson, MatchRule.class);
        } catch (Exception e) {
            log.warn("解析 conditions JSON 失败，视为无规则: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判定一条规则是否对当前请求成立：所有 condition 都成立（AND）。
     */
    private boolean matchRule(MatchRule rule, HttpServletRequest req) {
        for (MatchCondition c : rule.getConditions()) {
            String actual = extract(c.getSource(), c.getPath(), req);
            if (!evalOperator(c, actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从请求中提取指定路径的值。解析失败或字段不存在时返回 null。
     */
    String extract(String source, String path, HttpServletRequest req) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if ("QUERY".equalsIgnoreCase(source)) {
            return req.getParameter(path);
        }
        if ("BODY".equalsIgnoreCase(source)) {
            JsonNode root = getParsedBody(req);
            if (root == null) {
                return null;
            }
            JsonNode leaf = navigate(root, path);
            return leaf == null || leaf.isNull() || leaf.isMissingNode() ? null : leaf.asText();
        }
        return null;
    }

    /**
     * 获取请求体的 JsonNode（缓存在 request attribute 中，同请求多次条件复用）。
     * <p>
     * 原始 body 字符串应由 MockDispatchService 在 dispatch 开始时写入 ATTR_RAW_BODY；
     * 缺失时（兼容测试或异常路径）直接读 reader。JSON 乱码时返回 null 并记日志。
     */
    private JsonNode getParsedBody(HttpServletRequest req) {
        Object cached = req.getAttribute(ATTR_PARSED_BODY);
        if (cached instanceof JsonNode) {
            return (JsonNode) cached;
        }

        String rawBody = (String) req.getAttribute(ATTR_RAW_BODY);
        if (rawBody == null) {
            rawBody = readBody(req);
        }
        if (rawBody == null || rawBody.isEmpty()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(rawBody);
            req.setAttribute(ATTR_PARSED_BODY, node);
            return node;
        } catch (Exception e) {
            log.warn("请求体 JSON 解析失败，BODY 条件统一判不满足: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 兜底的 body 读取：仅在 request attribute 缺失时走（通常是单测或绕过 dispatch 的调用）。
     */
    private String readBody(HttpServletRequest req) {
        try {
            BufferedReader reader = req.getReader();
            if (reader == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("读取请求体失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 按点路径在 JsonNode 树中导航：
     * <ul>
     *   <li>user.addr.city → 逐层 get("user").get("addr").get("city")</li>
     *   <li>items[0].id → get("items").get(0).get("id")</li>
     *   <li>任一步 null / missing → 返回 null</li>
     * </ul>
     */
    JsonNode navigate(JsonNode root, String path) {
        JsonNode cur = root;
        for (String segment : path.split("\\.")) {
            if (cur == null || cur.isNull() || cur.isMissingNode()) {
                return null;
            }
            cur = visitSegment(cur, segment);
        }
        return cur;
    }

    /**
     * 访问一段路径。支持 {@code name}、{@code name[i]}、{@code [i]} 三种形式，
     * 其中方括号可连续：{@code name[0][1]}。
     */
    private JsonNode visitSegment(JsonNode node, String segment) {
        if (segment.isEmpty()) {
            return null;
        }
        int bracket = segment.indexOf('[');
        if (bracket < 0) {
            return node.get(segment);
        }
        JsonNode cur = bracket > 0 ? node.get(segment.substring(0, bracket)) : node;
        String rest = segment.substring(bracket);
        while (!rest.isEmpty()) {
            int close = rest.indexOf(']');
            if (!rest.startsWith("[") || close < 0) {
                return null;
            }
            String idxStr = rest.substring(1, close);
            try {
                int idx = Integer.parseInt(idxStr);
                if (cur == null) return null;
                cur = cur.get(idx);
            } catch (NumberFormatException e) {
                return null;
            }
            rest = rest.substring(close + 1);
        }
        return cur;
    }

    /**
     * 按操作符判定一条条件是否成立。
     * 除 IS_EMPTY 外，actual 为 null 一律返回 false。
     */
    boolean evalOperator(MatchCondition c, String actual) {
        String op = c.getOperator();
        if (op == null) {
            return false;
        }
        op = op.toUpperCase();

        // IS_EMPTY 是唯一 actual=null 返回 true 的操作符
        if (Operators.IS_EMPTY.equals(op)) {
            return isEmpty(actual);
        }

        if (actual == null) {
            return false;
        }

        String expected = c.getValue();
        boolean number = "NUMBER".equalsIgnoreCase(c.getValueType());

        switch (op) {
            case Operators.EQ: {
                if (!number) {
                    return actual.equals(expected);
                }
                int cmp = compareNumber(actual, expected);
                return cmp != Integer.MIN_VALUE && cmp == 0;
            }
            case Operators.NE: {
                if (!number) {
                    return !actual.equals(expected);
                }
                int cmp = compareNumber(actual, expected);
                // 解析失败视为不可比 → 判不成立（不说 "不等于"）
                return cmp != Integer.MIN_VALUE && cmp != 0;
            }
            case Operators.CONTAINS:
                return expected != null && actual.contains(expected);
            case Operators.IN:
                return inList(actual, expected, number);
            case Operators.GT: {
                int cmp = compareNumber(actual, expected);
                return cmp != Integer.MIN_VALUE && cmp > 0;
            }
            case Operators.GTE: {
                int cmp = compareNumber(actual, expected);
                return cmp != Integer.MIN_VALUE && cmp >= 0;
            }
            case Operators.LT: {
                int cmp = compareNumber(actual, expected);
                return cmp != Integer.MIN_VALUE && cmp < 0;
            }
            case Operators.LTE: {
                int cmp = compareNumber(actual, expected);
                return cmp != Integer.MIN_VALUE && cmp <= 0;
            }
            case Operators.REGEX:
                return matchRegex(actual, expected);
            default:
                log.warn("未知操作符: {}", op);
                return false;
        }
    }

    /**
     * 数字比较：返回 -1 / 0 / 1；解析失败返回 Integer.MIN_VALUE 表示"不可比"。
     */
    private int compareNumber(String actual, String expected) {
        try {
            BigDecimal a = new BigDecimal(actual);
            BigDecimal b = new BigDecimal(expected);
            return a.compareTo(b);
        } catch (Exception e) {
            log.warn("数字比较失败: actual={} expected={}", actual, expected);
            return Integer.MIN_VALUE;
        }
    }

    /**
     * IS_EMPTY 判定：null / 空字符串 / 空数组 / 空对象均为真。
     */
    private boolean isEmpty(String actual) {
        if (actual == null || actual.isEmpty()) {
            return true;
        }
        String trimmed = actual.trim();
        return "[]".equals(trimmed) || "{}".equals(trimmed);
    }

    /**
     * IN 操作符：expected 是 JSON 数组字符串，判断 actual 是否在其中。
     */
    private boolean inList(String actual, String expected, boolean number) {
        if (expected == null) {
            return false;
        }
        try {
            JsonNode arr = objectMapper.readTree(expected);
            if (!arr.isArray()) {
                return false;
            }
            for (JsonNode item : arr) {
                String itemStr = item.isTextual() ? item.asText() : item.toString();
                boolean eq = number ? compareNumber(actual, itemStr) == 0 : actual.equals(itemStr);
                if (eq) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("IN 操作符期望值不是合法 JSON 数组: {}", expected);
        }
        return false;
    }

    /**
     * REGEX 操作符：正则编译失败或匹配异常一律返回 false。
     */
    private boolean matchRegex(String actual, String regex) {
        if (regex == null) {
            return false;
        }
        try {
            return Pattern.compile(regex).matcher(actual).find();
        } catch (Exception e) {
            log.warn("正则编译或匹配失败: regex={} error={}", regex, e.getMessage());
            return false;
        }
    }

    /**
     * 操作符常量与校验用的枚举集合。
     */
    public static final class Operators {
        public static final String EQ = "EQ";
        public static final String NE = "NE";
        public static final String CONTAINS = "CONTAINS";
        public static final String IS_EMPTY = "IS_EMPTY";
        public static final String IN = "IN";
        public static final String GT = "GT";
        public static final String GTE = "GTE";
        public static final String LT = "LT";
        public static final String LTE = "LTE";
        public static final String REGEX = "REGEX";

        private static final Set<String> ALL = new HashSet<String>(Arrays.asList(
                EQ, NE, CONTAINS, IS_EMPTY, IN, GT, GTE, LT, LTE, REGEX));

        private static final Set<String> NUMERIC_ONLY = new HashSet<String>(Arrays.asList(GT, GTE, LT, LTE));

        public static boolean isValid(String op) {
            return op != null && ALL.contains(op.toUpperCase());
        }

        public static boolean requiresNumber(String op) {
            return op != null && NUMERIC_ONLY.contains(op.toUpperCase());
        }

        private Operators() {
        }
    }
}
