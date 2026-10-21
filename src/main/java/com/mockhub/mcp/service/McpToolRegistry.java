package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.mcp.model.McpSettings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具清单与 JSON Schema。工具名与设计稿保持一致（mockhub.*）。
 */
@Component
public class McpToolRegistry {

    public static final String SEARCH_APIS = "mockhub.search_apis";
    public static final String GET_API_DETAIL = "mockhub.get_api_detail";
    public static final String GET_API_STATS = "mockhub.get_api_stats";
    public static final String CREATE_API_DRAFT = "mockhub.create_api_draft";
    public static final String UPDATE_API_DRAFT = "mockhub.update_api_draft";
    public static final String APPLY_API_DRAFT = "mockhub.apply_api_draft";
    public static final String GENERATE_RESPONSE = "mockhub.generate_response_body";
    public static final String GENERATE_CONDITIONS = "mockhub.generate_conditions";
    public static final String EXPLAIN_MATCH = "mockhub.explain_match_result";

    private final ObjectMapper objectMapper;
    private final Map<String, Definition> tools = new LinkedHashMap<String, Definition>();

    public McpToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        register(new Definition(SEARCH_APIS, "搜索 Mock 接口",
                "按关键字搜索当前用户可见的 Mock 接口，返回方法、路径、名称与团队。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"搜索关键词，可为空\"},"
                        + "\"teamId\":{\"type\":\"string\",\"description\":\"团队 ID；超管可省略表示全部团队\"},"
                        + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20,\"default\":5}},"
                        + "\"required\":[\"query\"]}")));
        register(new Definition(GET_API_DETAIL, "读取接口详情",
                "读取一个接口的定义、返回体与条件规则；响应体最多返回 2KB。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"apiId\":{\"type\":\"string\",\"description\":\"接口 ID\"}},"
                        + "\"required\":[\"apiId\"]}")));
        register(new Definition(GET_API_STATS, "查询调用统计",
                "查询窗口内的调用总览与 Top 接口；超管可省略 teamId 查看全部团队。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"teamId\":{\"type\":\"string\",\"description\":\"团队 ID，可省略\"},"
                        + "\"days\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":30,\"default\":7},"
                        + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10,\"default\":5}}}")));
        register(new Definition(CREATE_API_DRAFT, "创建接口草稿",
                "根据参数创建接口草稿，返回待确认操作 ID 与确认链接；不会直接写入数据库。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"teamId\":{\"type\":\"string\",\"description\":\"团队 ID\"},"
                        + "\"name\":{\"type\":\"string\",\"description\":\"接口名称\"},"
                        + "\"method\":{\"type\":\"string\",\"enum\":[\"GET\",\"POST\",\"PUT\",\"DELETE\",\"PATCH\"]},"
                        + "\"path\":{\"type\":\"string\",\"description\":\"以 / 开头的路径，可含 {id}\"},"
                        + "\"responseBody\":{\"type\":\"string\",\"description\":\"单返回体响应体，默认 JSON\"},"
                        + "\"contentType\":{\"type\":\"string\",\"default\":\"application/json\"},"
                        + "\"responseMode\":{\"type\":\"string\",\"enum\":[\"CONDITION\",\"RANDOM\"],\"default\":\"CONDITION\"},"
                        + "\"responses\":{\"type\":\"array\",\"description\":\"多返回体；每项支持 name/responseCode/contentType/responseBody/bodyType/active/sortOrder/conditions/delayMs\",\"items\":{\"type\":\"object\"}},"
                        + "\"description\":{\"type\":\"string\",\"description\":\"接口描述\"}},"
                        + "\"required\":[\"teamId\",\"name\",\"method\",\"path\"]}")));
        register(new Definition(UPDATE_API_DRAFT, "修改接口草稿",
                "修改已有接口并生成待确认草稿；可整体替换 responses，或用 addResponses 追加返回体。确认后才会写入。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"apiId\":{\"type\":\"string\",\"description\":\"接口 ID\"},"
                        + "\"name\":{\"type\":\"string\"},"
                        + "\"description\":{\"type\":\"string\"},"
                        + "\"method\":{\"type\":\"string\"},"
                        + "\"path\":{\"type\":\"string\"},"
                        + "\"enabled\":{\"type\":\"boolean\"},"
                        + "\"responseMode\":{\"type\":\"string\",\"enum\":[\"CONDITION\",\"RANDOM\"]},"
                        + "\"responses\":{\"type\":\"array\",\"description\":\"多返回体；每项支持 name/responseCode/contentType/responseBody/bodyType/active/sortOrder/conditions/delayMs\",\"items\":{\"type\":\"object\"}},"
                        + "\"addResponses\":{\"type\":\"array\",\"description\":\"追加返回体，字段同 responses\",\"items\":{\"type\":\"object\"}}},"
                        + "\"required\":[\"apiId\"]}")));
        register(new Definition(APPLY_API_DRAFT, "应用接口草稿",
                "确认并写入草稿。仅在写策略为 DIRECT 且本工具开启时可用；默认返回确认链接。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"actionId\":{\"type\":\"string\",\"description\":\"待确认操作 ID\"}},"
                        + "\"required\":[\"actionId\"]}")));
        register(new Definition(GENERATE_RESPONSE, "生成响应体",
                "根据接口上下文生成 JSON 或 XML 响应体。需要 AI 已配置且生成能力开启。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"teamId\":{\"type\":\"string\"},"
                        + "\"apiName\":{\"type\":\"string\"},"
                        + "\"method\":{\"type\":\"string\"},"
                        + "\"path\":{\"type\":\"string\"},"
                        + "\"instruction\":{\"type\":\"string\",\"description\":\"自然语言补充要求\"},"
                        + "\"contentType\":{\"type\":\"string\",\"default\":\"application/json\"},"
                        + "\"mode\":{\"type\":\"string\",\"enum\":[\"NEW\",\"COMPLETE\"],\"default\":\"NEW\"},"
                        + "\"size\":{\"type\":\"string\",\"enum\":[\"BRIEF\",\"STANDARD\",\"RICH\"],\"default\":\"STANDARD\"}},"
                        + "\"required\":[\"teamId\"]}")));
        register(new Definition(GENERATE_CONDITIONS, "生成条件规则",
                "把自然语言描述转成 conditions 结构。需要 AI 已配置且条件生成能力开启。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"teamId\":{\"type\":\"string\"},"
                        + "\"instruction\":{\"type\":\"string\",\"description\":\"命中条件描述\"},"
                        + "\"bodySample\":{\"type\":\"string\",\"description\":\"Body 示例 JSON，用于校验字段\"},"
                        + "\"currentConditions\":{\"type\":\"string\",\"description\":\"现有 conditions JSON\"}},"
                        + "\"required\":[\"teamId\",\"instruction\"]}")));
        register(new Definition(EXPLAIN_MATCH, "解释匹配结果",
                "重放一次请求日志的命中链路并给出修正建议。需要 AI 诊断能力开启。",
                schema("{\"type\":\"object\",\"properties\":{"
                        + "\"requestLogId\":{\"type\":\"string\",\"description\":\"请求日志 ID\"}},"
                        + "\"required\":[\"requestLogId\"]}")));
    }

    public List<Definition> listEnabled(McpSettings settings) {
        List<Definition> result = new ArrayList<Definition>();
        for (Definition definition : tools.values()) {
            if (isEnabled(settings, definition.name)) {
                result.add(definition);
            }
        }
        return result;
    }

    public Definition get(String name) {
        return tools.get(name);
    }

    public boolean isEnabled(McpSettings settings, String name) {
        if (SEARCH_APIS.equals(name)) return settings.isToolSearchApis();
        if (GET_API_DETAIL.equals(name)) return settings.isToolGetApiDetail();
        if (GET_API_STATS.equals(name)) return settings.isToolGetApiStats();
        if (CREATE_API_DRAFT.equals(name)) return settings.isToolCreateApiDraft();
        if (UPDATE_API_DRAFT.equals(name)) return settings.isToolUpdateApiDraft();
        if (APPLY_API_DRAFT.equals(name)) return settings.isToolApplyApiDraft();
        if (GENERATE_RESPONSE.equals(name)) return settings.isToolGenerateResponseBody();
        if (GENERATE_CONDITIONS.equals(name)) return settings.isToolGenerateConditions();
        if (EXPLAIN_MATCH.equals(name)) return settings.isToolExplainMatchResult();
        return false;
    }

    private void register(Definition definition) {
        tools.put(definition.name, definition);
    }

    private JsonNode schema(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid MCP tool schema", e);
        }
    }

    public static class Definition {
        private final String name;
        private final String title;
        private final String description;
        private final JsonNode inputSchema;

        public Definition(String name, String title, String description, JsonNode inputSchema) {
            this.name = name;
            this.title = title;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", name);
            node.put("title", title);
            node.put("description", description);
            node.set("inputSchema", inputSchema);
            return node;
        }
    }
}