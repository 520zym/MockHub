package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiConditionSuggestion;
import com.mockhub.ai.model.dto.AiConditionsResult;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.common.model.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * AI 生成能力：响应体与条件规则。
 * <p>
 * 模型只负责产出内容，服务端负责：
 * 1. 访问控制（启用 / 范围 / 能力 / 团队）；
 * 2. 输出清洗与格式校验（JSON / XML）；
 * 3. 条件规则的字段与操作符合法性校验，明确标记不可用项。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final int MAX_CONTENT_LENGTH = 512 * 1024;
    private static final Set<String> OPERATORS = new HashSet<String>(Arrays.asList(
            "EQ", "NE", "CONTAINS", "IS_EMPTY", "IN", "GT", "GTE", "LT", "LTE", "REGEX"));
    private static final Set<String> NUMERIC_OPERATORS = new HashSet<String>(Arrays.asList(
            "GT", "GTE", "LT", "LTE"));
    private static final Set<String> SOURCES = new HashSet<String>(Arrays.asList("BODY", "QUERY", "HEADER"));

    /** 代码围栏正则使用 \x60 表示反引号，避免源文件出现三个连续反引号。 */
    private static final String FENCE_HEAD = "(?s)^\\s*\\x60{3}[a-zA-Z]*\\s*";
    private static final String FENCE_TAIL = "\\s*\\x60{3}\\s*$";

    private final AiSettingsService settingsService;
    private final OpenAiCompatibleProvider provider;
    private final MockAiGenerator mockGenerator;
    private final ObjectMapper objectMapper;

    public AiService(AiSettingsService settingsService,
                     OpenAiCompatibleProvider provider,
                     MockAiGenerator mockGenerator,
                     ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.provider = provider;
        this.mockGenerator = mockGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成响应体。
     */
    public AiResponseResult generateResponse(AiGenerateRequest req) {
        validateRequest(req);
        AiSettings settings = settingsService.assertUsable(AiSettingsService.CAP_RESPONSE, req.getTeamId());

        if (AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            return mockGenerator.response(req);
        }

        boolean xml = isXml(req);
        String raw = provider.complete(settings, responseSystemPrompt(xml), responseUserPrompt(req, xml));
        String content = extractContent(raw, xml);
        validateContent(content, xml);

        AiResponseResult result = new AiResponseResult();
        result.setContent(content);
        result.setContentType(xml ? "application/xml" : "application/json");
        result.setProvider(settings.getProvider());
        result.setModel(settings.getModel());
        return result;
    }

    /**
     * 生成条件规则。返回结果可能包含 valid=false 的规则，由前端默认不勾选。
     */
    public AiConditionsResult generateConditions(AiGenerateRequest req) {
        validateRequest(req);
        AiSettings settings = settingsService.assertUsable(AiSettingsService.CAP_CONDITIONS, req.getTeamId());

        if (AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            return mockGenerator.conditions(req);
        }

        String raw = provider.complete(settings, conditionsSystemPrompt(), conditionsUserPrompt(req));
        String json = extractJson(raw);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("AI 条件结果不是合法 JSON：{}", abbreviate(raw));
            throw new BizException(50213, "模型返回的条件规则不是合法 JSON，请重试");
        }
        JsonNode array = root.isArray() ? root : root.path("conditions");
        if (!array.isArray() || array.size() == 0) {
            throw new BizException(50213, "模型没有返回可用的条件规则，请补充更明确的字段与取值");
        }

        List<FieldInfo> sampled = flattenSample(req.getBodySample());
        AiConditionsResult result = new AiConditionsResult();
        result.setProvider(settings.getProvider());
        result.setModel(settings.getModel());

        int count = 0;
        for (JsonNode node : array) {
            if (count >= 10) {
                break;
            }
            AiConditionSuggestion suggestion = toSuggestion(node, sampled, result.getWarnings());
            if (suggestion != null) {
                result.getRules().add(suggestion);
                count++;
            }
        }
        if (result.getRules().isEmpty()) {
            throw new BizException(50213, "模型没有返回可用的条件规则");
        }
        return result;
    }

    private void validateRequest(AiGenerateRequest req) {
        if (req == null) {
            throw new BizException(40001, "请求不能为空");
        }
        if (req.getTeamId() == null || req.getTeamId().trim().isEmpty()) {
            throw new BizException(40001, "缺少团队信息");
        }
    }

    // ===== 提示词 =====

    private String responseSystemPrompt(boolean xml) {
        return "你是 MockHub 的 Mock 响应生成助手。"
                + "只输出" + (xml ? "合法的 XML" : "合法的 JSON") + "响应体本身，"
                + "不要输出 Markdown 代码块标记、解释文字或多段内容。"
                + "字段名使用英文小驼峰，字段值要贴合接口语义，必要时使用合理的假数据。";
    }

    private String responseUserPrompt(AiGenerateRequest req, boolean xml) {
        StringBuilder sb = new StringBuilder();
        sb.append("接口信息：\n");
        sb.append("- 类型：").append(nullToEmpty(req.getApiType(), "REST")).append("\n");
        sb.append("- 名称：").append(nullToEmpty(req.getApiName(), "未命名接口")).append("\n");
        if (req.getMethod() != null) {
            sb.append("- 方法：").append(req.getMethod()).append("\n");
        }
        if (req.getPath() != null) {
            sb.append("- 路径：").append(req.getPath()).append("\n");
        }
        if (req.getSoapOperationName() != null) {
            sb.append("- Operation：").append(req.getSoapOperationName()).append("\n");
        }
        sb.append("- 返回体名称：").append(nullToEmpty(req.getResponseName(), "默认返回体")).append("\n");
        sb.append("- 期望格式：").append(xml ? "XML" : "JSON").append("\n");
        sb.append("- 详细程度：").append(nullToEmpty(req.getSize(), "STANDARD")).append("\n");
        if ("COMPLETE".equalsIgnoreCase(req.getMode())) {
            sb.append("- 生成方式：在现有响应体基础上补全，保留已有字段和取值语义\n");
        }
        if (req.getInstruction() != null && !req.getInstruction().trim().isEmpty()) {
            sb.append("\n用户补充要求：\n").append(truncate(req.getInstruction(), 800)).append("\n");
        }
        if (req.getExistingBody() != null && !req.getExistingBody().trim().isEmpty()) {
            sb.append("\n现有响应体（参考，最多 2000 字符）：\n")
                    .append(truncate(req.getExistingBody(), 2000)).append("\n");
        }
        sb.append("\n只输出响应体内容。");
        return sb.toString();
    }

    private String conditionsSystemPrompt() {
        return "你是 MockHub 的响应条件生成助手。只输出 JSON，格式为："
                + "{\"conditions\":[{\"source\":\"BODY\",\"path\":\"user.type\",\"operator\":\"EQ\","
                + "\"value\":\"vip\",\"valueType\":\"STRING\"}]}。"
                + "source 只能是 BODY、QUERY 或 HEADER（HEADER 的 path 写请求头名称，如 X-Env）；operator 只能是 EQ/NE/CONTAINS/IS_EMPTY/IN/GT/GTE/LT/LTE/REGEX；"
                + "valueType 只能是 STRING/NUMBER/BOOLEAN；value 存放原始值，不要额外加引号；"
                + "IN 的 value 使用 JSON 数组字符串。不要输出解释或 Markdown。";
    }

    private String conditionsUserPrompt(AiGenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("接口：").append(nullToEmpty(req.getApiName(), "未命名接口"));
        if (req.getPath() != null) {
            sb.append("（").append(req.getPath()).append("）");
        }
        sb.append("\n用户描述：").append(truncate(nullToEmpty(req.getInstruction(), ""), 800)).append("\n");
        if (req.getCurrentConditions() != null && !req.getCurrentConditions().trim().isEmpty()) {
            sb.append("\n现有条件（不要重复）：\n")
                    .append(truncate(req.getCurrentConditions(), 1000)).append("\n");
        }
        List<FieldInfo> sampleFields = flattenSample(req.getBodySample());
        if (!sampleFields.isEmpty()) {
            sb.append("\nBody 示例中的可用字段（仅 BODY 可引用）：\n");
            int count = 0;
            for (FieldInfo field : sampleFields) {
                if (count >= 30) {
                    break;
                }
                sb.append("- ").append(field.path).append(" (").append(field.type).append(")\n");
                count++;
            }
        } else {
            sb.append("\n没有 Body 示例，字段名需要根据接口语义推断。\n");
        }
        sb.append("\n只输出 JSON。");
        return sb.toString();
    }

    // ===== 输出清洗与校验 =====

    private String extractContent(String raw, boolean xml) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BizException(50201, "模型返回为空，请重试");
        }
        String text = stripFence(raw.trim());
        if (xml) {
            int start = text.indexOf('<');
            if (start > 0) {
                text = text.substring(start);
            }
            int end = text.lastIndexOf('>');
            if (end >= 0) {
                text = text.substring(0, end + 1);
            }
        } else {
            int start = text.indexOf('{');
            int arrayStart = text.indexOf('[');
            if (start < 0 || (arrayStart >= 0 && arrayStart < start)) {
                start = arrayStart;
            }
            int end = text.lastIndexOf('}');
            int arrayEnd = text.lastIndexOf(']');
            if (arrayEnd > end) {
                end = arrayEnd;
            }
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BizException(50214, "生成内容过大，请缩小字段规模后重试");
        }
        return text;
    }

    private String extractJson(String raw) {
        String text = stripFence(raw == null ? "" : raw.trim());
        int start = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        if (start < 0 || (arrayStart >= 0 && arrayStart < start)) {
            start = arrayStart;
        }
        if (start > 0) {
            text = text.substring(start);
        }
        return text.trim();
    }

    private String stripFence(String text) {
        return text.replaceAll(FENCE_HEAD, "").replaceAll(FENCE_TAIL, "").trim();
    }

    private void validateContent(String content, boolean xml) {
        if (content == null || content.trim().isEmpty()) {
            throw new BizException(50201, "模型返回为空，请重试");
        }
        if (xml) {
            validateXml(content);
        } else {
            try {
                objectMapper.readTree(content);
            } catch (Exception e) {
                throw new BizException(50211, "模型返回的不是合法 JSON，请重试或补充更明确的描述");
            }
        }
    }

    private void validateXml(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(50212, "模型返回的不是合法 XML，请重试");
        }
    }

    // ===== 条件规则校验 =====

    private AiConditionSuggestion toSuggestion(JsonNode node, List<FieldInfo> sampled, List<String> warnings) {
        AiConditionSuggestion s = new AiConditionSuggestion();
        String source = node.path("source").asText("BODY").toUpperCase();
        String path = node.path("path").asText("");
        String operator = node.path("operator").asText("EQ").toUpperCase();
        String value = node.path("value").asText("");
        String valueType = node.path("valueType").asText("STRING").toUpperCase();

        s.setSource(source);
        s.setPath(path);
        s.setOperator(operator);
        s.setValue(value);
        s.setValueType(valueType);

        if (!SOURCES.contains(source)) {
            s.setValid(false);
            s.setWarning("不支持的来源：" + source);
            return s;
        }
        if (path.trim().isEmpty() || path.length() > 500) {
            s.setValid(false);
            s.setWarning("字段路径为空或过长");
            return s;
        }
        if (!OPERATORS.contains(operator)) {
            s.setValid(false);
            s.setWarning("不支持的操作符：" + operator);
            return s;
        }
        if (NUMERIC_OPERATORS.contains(operator) && !"NUMBER".equals(valueType)) {
            s.setValid(false);
            s.setWarning("比较类操作符需要 NUMBER 类型");
            return s;
        }
        if ("IN".equals(operator)) {
            try {
                JsonNode arr = objectMapper.readTree(value);
                if (!arr.isArray()) {
                    s.setValid(false);
                    s.setWarning("IN 的值需要 JSON 数组");
                    return s;
                }
            } catch (Exception e) {
                s.setValid(false);
                s.setWarning("IN 的值不是合法 JSON 数组");
                return s;
            }
        }
        if ("HEADER".equals(source) && !path.matches("[A-Za-z0-9-]+")) {
            s.setValid(false);
            s.setWarning("Header 名称只能包含字母、数字和短横线");
            return s;
        }
        if ("BODY".equals(source) && !sampled.isEmpty()) {
            boolean found = false;
            for (FieldInfo field : sampled) {
                if (field.path.equals(path) || field.path.startsWith(path + ".") || path.startsWith(field.path + ".")) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                s.setValid(false);
                s.setWarning("Body 示例中没有字段 " + path + "，请确认字段名");
            }
        }
        if (!s.isValid() && warnings.size() < 5) {
            warnings.add(s.getWarning());
        }
        return s;
    }

    private List<FieldInfo> flattenSample(String sample) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>();
        if (sample == null || sample.trim().isEmpty()) {
            return fields;
        }
        try {
            collect(objectMapper.readTree(sample), "", fields, 0);
        } catch (Exception ignored) {
            // 示例无效时按无字段处理
        }
        return fields;
    }

    private void collect(JsonNode node, String prefix, List<FieldInfo> out, int depth) {
        if (node == null || depth > 4 || out.size() > 50) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                collect(node.get(name), prefix.isEmpty() ? name : prefix + "." + name, out, depth + 1);
            }
            return;
        }
        if (node.isArray()) {
            if (node.size() > 0) {
                collect(node.get(0), prefix + "[0]", out, depth + 1);
            }
            return;
        }
        FieldInfo info = new FieldInfo();
        info.path = prefix;
        info.type = node.isNumber() ? "NUMBER" : node.isBoolean() ? "BOOLEAN" : "STRING";
        if (!info.path.isEmpty()) {
            out.add(info);
        }
    }

    private boolean isXml(AiGenerateRequest req) {
        if ("SOAP".equalsIgnoreCase(req.getApiType())) {
            return true;
        }
        String contentType = req.getContentType() == null ? "" : req.getContentType().toLowerCase();
        return contentType.contains("xml");
    }

    private String nullToEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }

    private static class FieldInfo {
        private String path;
        private String type;
    }
}