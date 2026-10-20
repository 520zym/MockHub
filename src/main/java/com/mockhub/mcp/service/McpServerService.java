package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.service.LogService;
import com.mockhub.mcp.model.McpSettings;
import com.mockhub.mcp.model.McpToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * MCP JSON-RPC 2.0 方法分发。只实现 tools 能力，不提供 resources/prompts。
 */
@Service
public class McpServerService {

    private static final Logger log = LoggerFactory.getLogger(McpServerService.class);

    private static final String PROTOCOL_LATEST = "2025-06-18";
    private static final Set<String> SUPPORTED_VERSIONS = new HashSet<String>(
            Arrays.asList("2025-06-18", "2024-11-05"));
    private static final int MAX_RESULT_CHARS = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final McpSettingsService settingsService;
    private final McpToolRegistry registry;
    private final McpToolsService toolsService;
    private final LogService logService;

    public McpServerService(ObjectMapper objectMapper,
                            McpSettingsService settingsService,
                            McpToolRegistry registry,
                            McpToolsService toolsService,
                            LogService logService) {
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.registry = registry;
        this.toolsService = toolsService;
        this.logService = logService;
    }

    /**
     * @return JSON-RPC 响应；通知返回 null（控制器回 202）
     */
    public ObjectNode handle(JsonNode request, McpToken token) {
        if (request == null || !request.isObject()) {
            return error(null, -32600, "Invalid Request");
        }
        JsonNode id = request.get("id");
        if (!"2.0".equals(request.path("jsonrpc").asText())) {
            return error(id, -32600, "jsonrpc 必须为 2.0");
        }
        String method = request.path("method").asText("");
        if (method.isEmpty()) {
            return error(id, -32600, "缺少 method");
        }
        if (id == null || id.isNull()) {
            // notification：接受并返回 null，由控制器返回 202
            return null;
        }

        JsonNode params = request.path("params");
        if ("initialize".equals(method)) {
            return initialize(id, params);
        }
        if ("ping".equals(method)) {
            return result(id, objectMapper.createObjectNode());
        }
        if ("tools/list".equals(method)) {
            return listTools(id);
        }
        if ("tools/call".equals(method)) {
            return callTool(id, params, token);
        }
        return error(id, -32601, "Method not found: " + method);
    }

    private ObjectNode initialize(JsonNode id, JsonNode params) {
        String requested = params.path("protocolVersion").asText("");
        String negotiated = SUPPORTED_VERSIONS.contains(requested) ? requested : PROTOCOL_LATEST;

        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", negotiated);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "mockhub");
        serverInfo.put("title", "MockHub");
        serverInfo.put("version", "1.5.0");
        result.put("instructions", "MockHub MCP：可搜索接口、查询统计、生成响应体/条件、创建接口草稿。写操作默认需要回到 MockHub 页面确认。");
        return result(id, result);
    }

    private ObjectNode listTools(JsonNode id) {
        McpSettings settings = settingsService.load();
        ArrayNode tools = objectMapper.createArrayNode();
        for (McpToolRegistry.Definition definition : registry.listEnabled(settings)) {
            tools.add(definition.toJson(objectMapper));
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
        return result(id, result);
    }

    private ObjectNode callTool(JsonNode id, JsonNode params, McpToken token) {
        String name = params.path("name").asText("");
        McpToolRegistry.Definition definition = registry.get(name);
        if (definition == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        if (!registry.isEnabled(settingsService.load(), name)) {
            return error(id, -32602, "Tool disabled: " + name);
        }
        JsonNode args = params.path("arguments");
        long started = System.currentTimeMillis();
        try {
            Object data = toolsService.execute(name, args);
            String text = toText(data);
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode content = result.putArray("content");
            content.addObject().put("type", "text").put("text", text);
            result.put("isError", false);
            audit(token, name, args, true, null, System.currentTimeMillis() - started);
            return result(id, result);
        } catch (BizException e) {
            log.warn("MCP 工具 {} 业务失败：{}", name, e.getMsg());
            audit(token, name, args, false, e.getMsg(), System.currentTimeMillis() - started);
            return toolError(id, e.getMsg());
        } catch (Exception e) {
            log.error("MCP 工具 {} 执行失败", name, e);
            audit(token, name, args, false, e.getMessage(), System.currentTimeMillis() - started);
            return toolError(id, "工具执行失败，请稍后重试");
        }
    }

    private ObjectNode toolError(JsonNode id, String message) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text").put("text", message == null ? "执行失败" : message);
        result.put("isError", true);
        return result(id, result);
    }

    private String toText(Object data) {
        try {
            String text = data instanceof String ? (String) data
                    : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            return text.length() <= MAX_RESULT_CHARS ? text
                    : text.substring(0, MAX_RESULT_CHARS) + "\n…（结果过大已截断）";
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    private void audit(McpToken token, String tool, JsonNode args, boolean success, String error, long costMs) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setId(UUID.randomUUID().toString());
            opLog.setTeamId(args != null && args.hasNonNull("teamId") ? args.get("teamId").asText() : null);
            opLog.setUserId(SecurityContextUtil.getCurrentUserId());
            opLog.setUsername(SecurityContextUtil.getCurrentUsername());
            opLog.setAction("MCP_CALL");
            opLog.setTargetType("MCP_TOOL");
            opLog.setTargetId(token.getId());
            opLog.setTargetName(tool);
            opLog.setDetail("MCP 工具 " + tool + "（token=" + token.getName() + "）："
                    + (success ? "成功" : "失败：" + error) + "，" + costMs + "ms");
            opLog.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            logService.logOperation(opLog);
        } catch (Exception e) {
            log.warn("记录 MCP 调用日志失败：{}", e.getMessage());
        }
    }

    private ObjectNode result(JsonNode id, ObjectNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? objectMapper.nullNode() : id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? objectMapper.nullNode() : id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }
}
