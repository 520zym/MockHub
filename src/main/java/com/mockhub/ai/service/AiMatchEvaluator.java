package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mockhub.mock.model.dto.match.MatchCondition;
import com.mockhub.mock.model.dto.match.MatchRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 只读条件评估：把历史请求快照按与 ResponseMatcher 相同的语义重放。
 * <p>
 * 与运行时匹配器保持同一套操作符语义，不在诊断路径上修改任何状态。
 */
@Component
public class AiMatchEvaluator {

    private final ObjectMapper objectMapper;

    public AiMatchEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MatchRule parseRule(String conditions) {
        if (conditions == null || conditions.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(conditions, MatchRule.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return 通过返回 null；未通过返回可读原因。
     */
    public String evaluate(MatchRule rule, String bodyJson, String paramsJson) {
        return evaluate(rule, bodyJson, paramsJson, null);
    }

    public String evaluate(MatchRule rule, String bodyJson, String paramsJson, String headersJson) {
        if (rule == null || rule.getConditions() == null || rule.getConditions().isEmpty()) {
            return null;
        }
        JsonNode body = parseJson(bodyJson);
        JsonNode params = parseJson(paramsJson);
        JsonNode headers = parseJson(headersJson);
        for (MatchCondition condition : rule.getConditions()) {
            String actual = extract(condition.getSource(), condition.getPath(), body, params, headers);
            if (!pass(condition, actual)) {
                return describe(condition, actual);
            }
        }
        return null;
    }

    private String describe(MatchCondition c, String actual) {
        String path = c.getPath();
        String actualText = actual == null ? "不存在" : "「" + actual + "」";
        if ("IS_EMPTY".equalsIgnoreCase(c.getOperator())) {
            return "字段 " + path + " 期望为空，实际" + actualText;
        }
        return "字段 " + path + " 期望「" + c.getValue() + "」，实际" + actualText;
    }

    private boolean pass(MatchCondition c, String actual) {
        String op = c.getOperator() == null ? "" : c.getOperator().toUpperCase();
        if ("IS_EMPTY".equals(op)) {
            return actual == null || actual.isEmpty() || "[]".equals(actual.trim()) || "{}".equals(actual.trim());
        }
        if (actual == null) {
            return false;
        }
        String expected = c.getValue();
        boolean number = "NUMBER".equalsIgnoreCase(c.getValueType());
        if ("EQ".equals(op)) {
            return number ? compare(actual, expected) == 0 : actual.equals(expected);
        }
        if ("NE".equals(op)) {
            int cmp = compare(actual, expected);
            return number ? cmp != Integer.MIN_VALUE && cmp != 0 : !actual.equals(expected);
        }
        if ("CONTAINS".equals(op)) {
            return expected != null && actual.contains(expected);
        }
        if ("IN".equals(op)) {
            try {
                JsonNode array = objectMapper.readTree(expected);
                for (JsonNode item : array) {
                    String value = item.isTextual() ? item.asText() : item.toString();
                    if (number ? compare(actual, value) == 0 : actual.equals(value)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                return false;
            }
            return false;
        }
        if ("GT".equals(op) || "GTE".equals(op) || "LT".equals(op) || "LTE".equals(op)) {
            int cmp = compare(actual, expected);
            if (cmp == Integer.MIN_VALUE) {
                return false;
            }
            if ("GT".equals(op)) return cmp > 0;
            if ("GTE".equals(op)) return cmp >= 0;
            if ("LT".equals(op)) return cmp < 0;
            return cmp <= 0;
        }
        if ("REGEX".equals(op)) {
            try {
                return expected != null && Pattern.compile(expected).matcher(actual).find();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private int compare(String actual, String expected) {
        try {
            return new BigDecimal(actual).compareTo(new BigDecimal(expected));
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private String extract(String source, String path, JsonNode body, JsonNode params, JsonNode headers) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if ("HEADER".equalsIgnoreCase(source)) {
            if (headers == null || !headers.isObject()) {
                return null;
            }
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = headers.fields();
            while (it.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = it.next();
                if (entry.getKey().equalsIgnoreCase(path)) {
                    return entry.getValue().isNull() ? null : entry.getValue().asText();
                }
            }
            return null;
        }
        if ("QUERY".equalsIgnoreCase(source)) {
            JsonNode value = params == null ? null : params.get(path);
            return value == null || value.isNull() ? null : value.asText();
        }
        if ("BODY".equalsIgnoreCase(source)) {
            JsonNode leaf = navigate(body, path);
            return leaf == null || leaf.isNull() || leaf.isMissingNode() ? null : leaf.asText();
        }
        return null;
    }

    private JsonNode navigate(JsonNode root, String path) {
        if (root == null) {
            return MissingNode.getInstance();
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return MissingNode.getInstance();
            }
            int bracket = segment.indexOf('[');
            if (bracket < 0) {
                current = current.get(segment);
                continue;
            }
            if (bracket > 0) {
                current = current.get(segment.substring(0, bracket));
            }
            String rest = segment.substring(bracket);
            while (rest.startsWith("[") && rest.contains("]")) {
                int close = rest.indexOf(']');
                try {
                    current = current == null ? null : current.get(Integer.parseInt(rest.substring(1, close)));
                } catch (Exception e) {
                    return MissingNode.getInstance();
                }
                rest = rest.substring(close + 1);
            }
        }
        return current == null ? MissingNode.getInstance() : current;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return MissingNode.getInstance();
        }
    }
}