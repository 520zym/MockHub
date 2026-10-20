package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.ai.model.dto.AiConditionSuggestion;
import com.mockhub.ai.model.dto.AiConditionsResult;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.ai.model.dto.AiResponseResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置演示模型：不访问外部服务，按规则产出结构合理的示例。
 * <p>
 * 用途：
 * 1. 未配置真实模型时的离线演示；
 * 2. 单元测试的确定性依赖。
 */
@Component
public class MockAiGenerator {

    private final ObjectMapper objectMapper;

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_.\\[\\]]*)\\s*(?:是|等于|为|=)\\s*[\"']?([a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+)[\"']?");

    public MockAiGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 生成 JSON / XML 响应体。根据接口名称与路径挑选字段模板，保证示例可读。
     */
    public AiResponseResult response(AiGenerateRequest req) {
        AiResponseResult result = new AiResponseResult();
        result.setProvider("MOCK");
        result.setModel("内置演示模型");

        boolean xml = isXml(req);
        String size = req.getSize() == null ? "STANDARD" : req.getSize().toUpperCase();
        boolean complete = "COMPLETE".equalsIgnoreCase(req.getMode())
                && req.getExistingBody() != null && !req.getExistingBody().trim().isEmpty();

        if (complete) {
            String merged = mergeExisting(req.getExistingBody(), xml, size);
            result.setContent(merged);
            result.setContentType(xml ? "application/xml" : "application/json");
            result.getWarnings().add("演示模式：已在现有内容基础上补充字段，请确认后插入");
            return result;
        }

        String keyword = ((req.getApiName() == null ? "" : req.getApiName())
                + " " + (req.getPath() == null ? "" : req.getPath())).toLowerCase();

        if (xml) {
            result.setContent(xmlTemplate(req, keyword, size));
            result.setContentType("application/xml");
        } else if (keyword.contains("user") || keyword.contains("用户") || keyword.contains("member")) {
            result.setContent(jsonUser(size));
            result.setContentType("application/json");
        } else if (keyword.contains("order") || keyword.contains("订单")) {
            result.setContent(jsonOrder(size));
            result.setContentType("application/json");
        } else if (keyword.contains("pay") || keyword.contains("支付") || keyword.contains("refund")) {
            result.setContent(jsonPay(size));
            result.setContentType("application/json");
        } else {
            result.setContent(jsonDefault(req, size));
            result.setContentType("application/json");
        }
        return result;
    }

    /**
     * 条件生成：从自然语言中提取「字段 等于 值」组合，并对照 Body 示例标记可用性。
     * <p>
     * 当前匹配引擎只支持 BODY / QUERY，遇到 header 描述会显式提示。
     */
    public AiConditionsResult conditions(AiGenerateRequest req) {
        AiConditionsResult result = new AiConditionsResult();
        result.setProvider("MOCK");
        result.setModel("内置演示模型");

        List<FieldInfo> fields = parseFields(req.getBodySample());
        String instruction = req.getInstruction() == null ? "" : req.getInstruction();
        List<AiConditionSuggestion> rules = new ArrayList<AiConditionSuggestion>();

        if (instruction.toLowerCase().contains("header")) {
            result.getWarnings().add("当前版本仅支持 Body / Query 条件，Header 条件未生成");
        }

        Matcher matcher = CONDITION_PATTERN.matcher(instruction);
        while (matcher.find() && rules.size() < 4) {
            String field = matcher.group(1);
            String value = matcher.group(2);
            String source = resolveSource(field, instruction);
            String path = source.equals("QUERY") ? lastSegment(field) : field;
            AiConditionSuggestion rule = new AiConditionSuggestion();
            rule.setSource(source);
            rule.setPath(path);
            rule.setValue(value);
            rule.setOperator(resolveOperator(instruction, value));
            rule.setValueType(isNumeric(value) ? "NUMBER" : "STRING");
            validateRule(rule, fields);
            rules.add(rule);
        }

        if (rules.isEmpty()) {
            // 没有显式字段时，从示例中挑前两个叶子字段，生成可编辑的占位规则
            for (int i = 0; i < Math.min(2, fields.size()); i++) {
                FieldInfo field = fields.get(i);
                AiConditionSuggestion rule = new AiConditionSuggestion();
                rule.setSource("BODY");
                rule.setPath(field.path);
                rule.setValue(field.sample);
                rule.setOperator("EQ");
                rule.setValueType(field.type);
                validateRule(rule, fields);
                rules.add(rule);
            }
        }

        if (rules.isEmpty()) {
            AiConditionSuggestion fallback = new AiConditionSuggestion();
            fallback.setSource("BODY");
            fallback.setPath("status");
            fallback.setOperator("EQ");
            fallback.setValue("success");
            fallback.setValueType("STRING");
            fallback.setValid(false);
            fallback.setWarning("没有可参考的 Body 示例，请确认字段名");
            rules.add(fallback);
        }

        result.setRules(rules);
        return result;
    }

    // ===== 响应体模板 =====

    private String jsonUser(String size) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("code", 0);
        root.put("message", "success");
        ObjectNode data = root.putObject("data");
        data.put("id", "10086");
        data.put("name", "张三");
        data.put("avatar", "https://cdn.mockhub.dev/avatar/10086.png");
        data.put("level", "VIP");
        if (!"BRIEF".equals(size)) {
            data.put("registeredAt", "2024-03-18T09:24:00+08:00");
            ObjectNode stats = data.putObject("stats");
            stats.put("orders", 42);
            stats.put("coupons", 3);
        }
        if ("RICH".equals(size)) {
            data.put("email", "zhangsan@example.com");
            data.put("phone", "138****8000");
            ArrayNode tags = data.putArray("tags");
            tags.add("new-user");
            tags.add("vip");
        }
        return pretty(root);
    }

    private String jsonOrder(String size) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("code", 0);
        root.put("message", "success");
        ObjectNode data = root.putObject("data");
        data.put("orderNo", "202606230001");
        data.put("status", "PAID");
        data.put("amount", 128.5);
        data.put("currency", "CNY");
        if (!"BRIEF".equals(size)) {
            data.put("createdAt", "2026-06-23T16:40:00+08:00");
            ArrayNode items = data.putArray("items");
            ObjectNode item = items.addObject();
            item.put("skuId", "SKU-001");
            item.put("name", "年度会员");
            item.put("count", 1);
        }
        if ("RICH".equals(size)) {
            data.put("userId", "10086");
            data.put("remark", "示例订单，可用于联调");
        }
        return pretty(root);
    }

    private String jsonPay(String size) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("code", 0);
        root.put("message", "success");
        ObjectNode data = root.putObject("data");
        data.put("payId", "PAY-20260623-0001");
        data.put("status", "SUCCESS");
        data.put("amount", 99.0);
        if (!"BRIEF".equals(size)) {
            data.put("paidAt", "2026-06-23T17:04:22+08:00");
            data.put("channel", "WECHAT");
        }
        return pretty(root);
    }

    private String jsonDefault(AiGenerateRequest req, String size) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("code", 0);
        root.put("message", "success");
        ObjectNode data = root.putObject("data");
        data.put("id", "demo-001");
        data.put("name", req.getApiName() == null ? "示例数据" : req.getApiName());
        data.put("status", "ACTIVE");
        if (!"BRIEF".equals(size)) {
            data.put("createdAt", "2026-06-23T16:40:00+08:00");
            data.put("updatedAt", "2026-06-23T17:04:22+08:00");
        }
        return pretty(root);
    }

    private String xmlTemplate(AiGenerateRequest req, String keyword, String size) {
        String operation = req.getSoapOperationName();
        if (operation == null || operation.isEmpty()) {
            operation = "response";
        }
        StringBuilder sb = new StringBuilder();
        if ("SOAP".equalsIgnoreCase(req.getApiType())) {
            sb.append("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n");
            sb.append("  <soapenv:Body>\n");
            sb.append("    <ns:").append(operation).append("Response xmlns:ns=\"http://mockhub.local/soap\">\n");
            sb.append("      <returnCode>0</returnCode>\n");
            sb.append("      <returnMessage>success</returnMessage>\n");
            sb.append("      <data>\n");
            sb.append("        <id>10086</id>\n");
            sb.append("        <status>OK</status>\n");
            sb.append("      </data>\n");
            sb.append("    </ns:").append(operation).append("Response>\n");
            sb.append("  </soapenv:Body>\n");
            sb.append("</soapenv:Envelope>");
        } else {
            sb.append("<response>\n");
            sb.append("  <code>0</code>\n");
            sb.append("  <message>success</message>\n");
            sb.append("  <data>\n");
            sb.append("    <id>10086</id>\n");
            sb.append("    <name>示例数据</name>\n");
            if (!"BRIEF".equals(size)) {
                sb.append("    <createdAt>2026-06-23T16:40:00+08:00</createdAt>\n");
            }
            sb.append("  </data>\n");
            sb.append("</response>");
        }
        return sb.toString();
    }

    private String mergeExisting(String existing, boolean xml, String size) {
        if (xml) {
            return existing;
        }
        try {
            JsonNode node = objectMapper.readTree(existing);
            if (node.isObject()) {
                ObjectNode object = (ObjectNode) node;
                if (!object.has("updatedAt")) {
                    object.put("updatedAt", "2026-06-23T17:04:22+08:00");
                }
                return pretty(object);
            }
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }

    // ===== 条件解析辅助 =====

    private List<FieldInfo> parseFields(String sample) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>();
        if (sample == null || sample.trim().isEmpty()) {
            return fields;
        }
        try {
            JsonNode root = objectMapper.readTree(sample);
            collectFields(root, "", fields, 0);
        } catch (Exception ignored) {
            // 示例不是合法 JSON 时按无字段处理，由前端提示
        }
        return fields;
    }

    private void collectFields(JsonNode node, String prefix, List<FieldInfo> out, int depth) {
        if (node == null || depth > 4 || out.size() > 40) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String path = prefix.isEmpty() ? name : prefix + "." + name;
                collectFields(node.get(name), path, out, depth + 1);
            }
            return;
        }
        if (node.isArray()) {
            if (node.size() > 0) {
                String path = prefix + "[0]";
                collectFields(node.get(0), path, out, depth + 1);
            }
            return;
        }
        FieldInfo info = new FieldInfo();
        info.path = prefix;
        if (node.isNumber()) {
            info.type = "NUMBER";
        } else if (node.isBoolean()) {
            info.type = "BOOLEAN";
        } else {
            info.type = "STRING";
        }
        info.sample = node.asText();
        if (!info.path.isEmpty()) {
            out.add(info);
        }
    }

    private void validateRule(AiConditionSuggestion rule, List<FieldInfo> fields) {
        if (rule.getPath() == null || rule.getPath().trim().isEmpty()) {
            rule.setValid(false);
            rule.setWarning("字段路径为空");
            return;
        }
        if (!"BODY".equals(rule.getSource()) && !"QUERY".equals(rule.getSource())) {
            rule.setValid(false);
            rule.setWarning("不支持的来源：" + rule.getSource());
            return;
        }
        if (("GT".equals(rule.getOperator()) || "GTE".equals(rule.getOperator())
                || "LT".equals(rule.getOperator()) || "LTE".equals(rule.getOperator()))
                && !"NUMBER".equals(rule.getValueType())) {
            rule.setValid(false);
            rule.setWarning("比较类操作符需要 NUMBER 类型");
            return;
        }
        if ("BODY".equals(rule.getSource()) && !fields.isEmpty()) {
            boolean found = false;
            for (FieldInfo field : fields) {
                if (field.path.equals(rule.getPath()) || field.path.startsWith(rule.getPath() + ".")) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                rule.setValid(false);
                rule.setWarning("Body 示例中没有字段 " + rule.getPath() + "，请确认字段名");
            }
        }
    }

    private String resolveSource(String field, String instruction) {
        String lower = instruction.toLowerCase();
        if (lower.contains("query") || lower.contains("参数") || lower.contains("查询")) {
            return "QUERY";
        }
        return "BODY";
    }

    private String resolveOperator(String instruction, String value) {
        if (instruction.contains("大于等于")) return "GTE";
        if (instruction.contains("小于等于")) return "LTE";
        if (instruction.contains("大于")) return "GT";
        if (instruction.contains("小于")) return "LT";
        if (instruction.contains("包含")) return "CONTAINS";
        if (instruction.contains("不等于") || instruction.contains("不是")) return "NE";
        return "EQ";
    }

    private boolean isNumeric(String value) {
        return value != null && value.matches("-?\\d+(\\.\\d+)?");
    }

    private String lastSegment(String field) {
        int dot = field.lastIndexOf('.');
        return dot >= 0 ? field.substring(dot + 1) : field;
    }

    private String pretty(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private boolean isXml(AiGenerateRequest req) {
        if ("SOAP".equalsIgnoreCase(req.getApiType())) {
            return true;
        }
        String contentType = req.getContentType() == null ? "" : req.getContentType().toLowerCase();
        return contentType.contains("xml");
    }

    private static class FieldInfo {
        private String path;
        private String type;
        private String sample;
    }
}
