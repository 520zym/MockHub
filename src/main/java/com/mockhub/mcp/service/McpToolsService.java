package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.dto.AiConditionsResult;
import com.mockhub.ai.model.dto.AiExplainResult;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.ai.service.AiExplainService;
import com.mockhub.ai.service.AiService;
import com.mockhub.ai.service.PendingActionService;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mcp.service.McpSettingsService;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiDefinitionDetailVO;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.service.ApiService;
import com.mockhub.stats.model.dto.StatsOverviewVO;
import com.mockhub.stats.model.dto.TopApiVO;
import com.mockhub.stats.service.StatsService;
import com.mockhub.system.controller.HealthController;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具实现：复用现有业务服务，不绕过权限与团队隔离。
 */
@Service
public class McpToolsService {

    private final ObjectMapper objectMapper;
    private final ApiService apiService;
    private final StatsService statsService;
    private final AiService aiService;
    private final AiExplainService explainService;
    private final PendingActionService pendingActionService;
    private final PermissionChecker permissionChecker;
    private final McpSettingsService settingsService;
    private final HealthController healthController;

    public McpToolsService(ObjectMapper objectMapper,
                           ApiService apiService,
                           StatsService statsService,
                           AiService aiService,
                           AiExplainService explainService,
                           PendingActionService pendingActionService,
                           PermissionChecker permissionChecker,
                           McpSettingsService settingsService,
                           HealthController healthController) {
        this.objectMapper = objectMapper;
        this.apiService = apiService;
        this.statsService = statsService;
        this.aiService = aiService;
        this.explainService = explainService;
        this.pendingActionService = pendingActionService;
        this.permissionChecker = permissionChecker;
        this.settingsService = settingsService;
        this.healthController = healthController;
    }

    public Object execute(String name, JsonNode args) {
        if (McpToolRegistry.SEARCH_APIS.equals(name)) {
            return searchApis(args);
        }
        if (McpToolRegistry.GET_API_DETAIL.equals(name)) {
            return getApiDetail(args);
        }
        if (McpToolRegistry.GET_API_STATS.equals(name)) {
            return getStats(args);
        }
        if (McpToolRegistry.CREATE_API_DRAFT.equals(name)) {
            return createDraft(args);
        }
        if (McpToolRegistry.APPLY_API_DRAFT.equals(name)) {
            return applyDraft(args);
        }
        if (McpToolRegistry.GENERATE_RESPONSE.equals(name)) {
            return generateResponse(args);
        }
        if (McpToolRegistry.GENERATE_CONDITIONS.equals(name)) {
            return generateConditions(args);
        }
        if (McpToolRegistry.EXPLAIN_MATCH.equals(name)) {
            return explain(args);
        }
        throw new BizException(40001, "未知工具：" + name);
    }

    private Object searchApis(JsonNode args) {
        String query = text(args, "query");
        String teamId = blankToNull(text(args, "teamId"));
        int limit = boundedInt(args, "limit", 5, 1, 20);
        PageResult<ApiDefinitionVO> page = apiService.list(teamId, null, null, null,
                query, null, null, null, null, 1, limit);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("total", page.getTotal());
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (ApiDefinitionVO api : page.getItems()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("apiId", api.getId());
            item.put("name", api.getName());
            item.put("method", api.getMethod());
            item.put("path", api.getPath());
            item.put("teamId", api.getTeamId());
            item.put("teamName", api.getTeamName());
            item.put("enabled", api.isEnabled());
            items.add(item);
        }
        result.put("items", items);
        return result;
    }

    private Object getApiDetail(JsonNode args) {
        String apiId = required(args, "apiId");
        ApiDefinitionDetailVO detail = apiService.getById(apiId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("apiId", detail.getId());
        result.put("name", detail.getName());
        result.put("teamId", detail.getTeamId());
        result.put("type", detail.getType());
        result.put("method", detail.getMethod());
        result.put("path", detail.getPath());
        result.put("enabled", detail.isEnabled());
        result.put("responseMode", detail.getResponseMode());
        result.put("description", detail.getDescription());
        List<Map<String, Object>> responses = new ArrayList<Map<String, Object>>();
        if (detail.getResponses() != null) {
            for (ApiResponse response : detail.getResponses()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("responseId", response.getId());
                item.put("name", response.getName());
                item.put("responseCode", response.getResponseCode());
                item.put("contentType", response.getContentType());
                item.put("active", response.isActive());
                item.put("conditions", response.getConditions());
                item.put("body", truncate(response.getResponseBody(), 2000));
                responses.add(item);
            }
        }
        result.put("responses", responses);
        return result;
    }

    private Object getStats(JsonNode args) {
        String teamId = blankToNull(text(args, "teamId"));
        int days = boundedInt(args, "days", 7, 1, 30);
        int limit = boundedInt(args, "limit", 5, 1, 10);
        StatsOverviewVO overview = statsService.getOverview(teamId, days);
        List<TopApiVO> top = statsService.getTopApis(teamId, days, limit);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("days", days);
        result.put("teamId", teamId);
        result.put("enabledApiCount", overview.getEnabledApiCount());
        result.put("todayCallCount", overview.getTodayCallCount());
        result.put("windowCallCount", overview.getWindowCallCount());
        result.put("avgDurationMs", overview.getAvgDurationMs());
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (TopApiVO api : top) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("apiId", api.getApiId());
            item.put("method", api.getMethod());
            item.put("path", api.getPath());
            item.put("name", api.getName());
            item.put("teamId", api.getTeamId());
            item.put("teamIdentifier", api.getTeamIdentifier());
            item.put("callCount", api.getCallCount());
            items.add(item);
        }
        result.put("topApis", items);
        return result;
    }

    private Object createDraft(JsonNode args) {
        String teamId = required(args, "teamId");
        permissionChecker.checkTeamAccess(teamId);
        String method = required(args, "method").toUpperCase();
        if (!method.matches("GET|POST|PUT|DELETE|PATCH")) {
            throw new BizException(40001, "不支持的 HTTP 方法：" + method);
        }
        String path = required(args, "path");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String name = text(args, "name");
        if (name.isEmpty()) {
            name = "MCP 创建的接口";
        }
        String body = text(args, "responseBody");
        if (body.isEmpty()) {
            body = "{\n  \"code\": 0,\n  \"message\": \"success\"\n}";
        }
        String contentType = text(args, "contentType");
        if (contentType.isEmpty()) {
            contentType = "application/json";
        }
        String conflict = apiService.findConflictingApiName(teamId, method, path, null);
        if (conflict != null) {
            throw new BizException(40401, "该团队已存在相同路径与方法的接口：" + conflict);
        }

        ApiDefinitionDTO dto = new ApiDefinitionDTO();
        dto.setTeamId(teamId);
        dto.setType("REST");
        dto.setName(name);
        dto.setDescription(text(args, "description"));
        dto.setMethod(method);
        dto.setPath(path);
        dto.setResponseCode(200);
        dto.setContentType(contentType);
        dto.setResponseBody(body);
        dto.setEnabled(true);
        dto.setResponseMode("CONDITION");
        ApiResponseDTO first = new ApiResponseDTO();
        first.setName("成功响应");
        first.setResponseCode(200);
        first.setContentType(contentType);
        first.setResponseBody(body);
        first.setBodyType("TEXT");
        first.setActive(true);
        first.setSortOrder(0);
        dto.setResponses(Collections.singletonList(first));

        Map<String, Object> action = pendingActionService.createApiDraft(dto, method + " " + path);
        String actionId = String.valueOf(action.get("actionId"));
        Map<String, Object> result = new LinkedHashMap<String, Object>(action);
        result.put("confirmUrl", confirmUrl(actionId));
        result.put("preview", truncate(body, 1200));
        return result;
    }

    private Object applyDraft(JsonNode args) {
        String actionId = required(args, "actionId");
        if (!com.mockhub.mcp.model.McpSettings.MODE_DIRECT.equals(getWriteMode())) {
            throw new BizException(40903, "当前 MCP 写策略为草稿确认，请打开确认链接完成创建："
                    + confirmUrl(actionId));
        }
        return pendingActionService.confirm(actionId);
    }

    private Object generateResponse(JsonNode args) {
        String teamId = required(args, "teamId");
        AiGenerateRequest request = new AiGenerateRequest();
        request.setTeamId(teamId);
        request.setApiType("REST");
        request.setApiName(text(args, "apiName"));
        request.setMethod(text(args, "method"));
        request.setPath(text(args, "path"));
        request.setContentType(text(args, "contentType"));
        request.setInstruction(text(args, "instruction"));
        request.setMode(text(args, "mode"));
        request.setSize(text(args, "size"));
        AiResponseResult result = aiService.generateResponse(request);
        return result;
    }

    private Object generateConditions(JsonNode args) {
        String teamId = required(args, "teamId");
        AiGenerateRequest request = new AiGenerateRequest();
        request.setTeamId(teamId);
        request.setInstruction(required(args, "instruction"));
        request.setBodySample(text(args, "bodySample"));
        request.setCurrentConditions(text(args, "currentConditions"));
        AiConditionsResult result = aiService.generateConditions(request);
        return result;
    }

    private Object explain(JsonNode args) {
        String requestLogId = required(args, "requestLogId");
        AiExplainResult result = explainService.explain(requestLogId);
        return result;
    }

    private String getWriteMode() {
        return settingsService.getWriteMode();
    }

    private String confirmUrl(String actionId) {
        String address = null;
        try {
            Map<String, String> serverAddress = healthController.serverAddress();
            address = serverAddress.get("address");
        } catch (Exception ignored) {
            // fall through
        }
        if (address == null || address.trim().isEmpty()) {
            address = "http://localhost:18080";
        }
        while (address.endsWith("/")) {
            address = address.substring(0, address.length() - 1);
        }
        return address + "/#/mcp-confirm/" + actionId;
    }

    private String required(JsonNode args, String field) {
        String value = text(args, field);
        if (value.isEmpty()) {
            throw new BizException(40001, "缺少参数：" + field);
        }
        return value;
    }

    private String text(JsonNode args, String field) {
        if (args == null) {
            return "";
        }
        JsonNode node = args.get(field);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private int boundedInt(JsonNode args, String field, int defaultValue, int min, int max) {
        if (args == null || !args.has(field)) {
            return defaultValue;
        }
        int value = args.get(field).asInt(defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}