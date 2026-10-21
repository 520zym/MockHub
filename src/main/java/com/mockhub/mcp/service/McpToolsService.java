package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.dto.AiApiFullUpdateDraft;
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
import com.mockhub.mock.service.match.ResponseValidator;
import com.mockhub.mock.model.entity.Tag;
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
        if (McpToolRegistry.UPDATE_API_DRAFT.equals(name)) {
            return updateDraft(args);
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
                item.put("bodyType", response.getBodyType());
                item.put("sortOrder", response.getSortOrder());
                item.put("delayMs", response.getDelayMs());
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
        String responseMode = normalizeResponseMode(text(args, "responseMode"));
        String conflict = apiService.findConflictingApiName(teamId, method, path, null);
        if (conflict != null) {
            throw new BizException(40401, "该团队已存在相同路径与方法的接口：" + conflict);
        }

        List<ApiResponseDTO> responses = parseResponses(args, body, contentType, "成功响应");
        validateResponses(responses, responseMode);

        ApiDefinitionDTO dto = new ApiDefinitionDTO();
        dto.setTeamId(teamId);
        dto.setType("REST");
        dto.setName(name);
        dto.setDescription(text(args, "description"));
        dto.setMethod(method);
        dto.setPath(path);
        dto.setResponseCode(200);
        dto.setContentType(contentType);
        dto.setResponseBody(responses.get(0).getResponseBody());
        dto.setEnabled(true);
        dto.setResponseMode(responseMode);
        dto.setResponses(responses);

        Map<String, Object> action = pendingActionService.createApiDraft(dto, method + " " + path);
        String actionId = String.valueOf(action.get("actionId"));
        Map<String, Object> result = new LinkedHashMap<String, Object>(action);
        result.put("confirmUrl", confirmUrl(actionId));
        result.put("responseCount", responses.size());
        result.put("preview", truncate(responses.get(0).getResponseBody(), 8000));
        return result;
    }

    private Object updateDraft(JsonNode args) {
        String apiId = required(args, "apiId");
        ApiDefinitionDetailVO detail = apiService.getById(apiId);
        permissionChecker.checkTeamAccess(detail.getTeamId());

        ApiDefinitionDTO dto = new ApiDefinitionDTO();
        dto.setTeamId(detail.getTeamId());
        dto.setGroupId(detail.getGroupId());
        dto.setType(detail.getType());
        dto.setName(detail.getName());
        dto.setDescription(detail.getDescription());
        dto.setMethod(detail.getMethod());
        dto.setPath(detail.getPath());
        dto.setResponseCode(detail.getResponseCode());
        dto.setContentType(detail.getContentType());
        dto.setResponseBody(detail.getResponseBody());
        dto.setDelayMs(detail.getDelayMs());
        dto.setEnabled(detail.isEnabled());
        dto.setResponseMode(normalizeResponseMode(detail.getResponseMode()));
        if (detail.getGlobalHeaderOverrides() != null && !detail.getGlobalHeaderOverrides().trim().isEmpty()) {
            try {
                dto.setGlobalHeaderOverrides(objectMapper.readValue(detail.getGlobalHeaderOverrides(), Map.class));
            } catch (Exception ignored) {
                // 老数据格式异常时不阻塞修改
            }
        }
        dto.setSoapConfig(detail.getSoapConfig());
        List<String> tagIds = new ArrayList<String>();
        if (detail.getTags() != null) {
            for (Tag tag : detail.getTags()) {
                tagIds.add(tag.getId());
            }
        }
        dto.setTagIds(tagIds);

        if (args.has("name") && !text(args, "name").isEmpty()) dto.setName(text(args, "name"));
        if (args.has("description")) dto.setDescription(text(args, "description"));
        if (args.has("method") && !text(args, "method").isEmpty()) dto.setMethod(text(args, "method").toUpperCase());
        if (args.has("path") && !text(args, "path").isEmpty()) {
            String path = text(args, "path");
            dto.setPath(path.startsWith("/") ? path : "/" + path);
        }
        if (args.has("enabled")) dto.setEnabled(args.get("enabled").asBoolean(dto.isEnabled()));
        if (args.has("responseMode") && !text(args, "responseMode").isEmpty()) {
            dto.setResponseMode(normalizeResponseMode(text(args, "responseMode")));
        }

        List<ApiResponseDTO> responses;
        if (args.has("responses") && args.get("responses").isArray() && args.get("responses").size() > 0) {
            responses = parseResponses(args, dto.getResponseBody(), dto.getContentType(), "响应体");
        } else {
            responses = existingResponseDtos(detail);
            if (args.has("addResponses") && args.get("addResponses").isArray()) {
                List<ApiResponseDTO> added = parseResponseArray(args.get("addResponses"), dto.getResponseBody(), dto.getContentType(), responses.size());
                responses.addAll(added);
            }
        }
        if (responses.isEmpty()) {
            throw new BizException(40001, "至少需要一个返回体");
        }
        validateResponses(responses, dto.getResponseMode());
        dto.setResponses(responses);
        dto.setResponseBody(responses.get(0).getResponseBody());

        String conflict = apiService.findConflictingApiName(dto.getTeamId(), dto.getMethod(), dto.getPath(), apiId);
        if (conflict != null) {
            throw new BizException(40401, "该团队已存在相同路径与方法的接口：" + conflict);
        }

        AiApiFullUpdateDraft draft = new AiApiFullUpdateDraft();
        draft.setApiId(apiId);
        draft.setTeamId(detail.getTeamId());
        draft.setDto(dto);
        Map<String, Object> action = pendingActionService.createApiFullUpdateDraft(
                draft, dto.getMethod() + " " + dto.getPath());
        String actionId = String.valueOf(action.get("actionId"));
        Map<String, Object> result = new LinkedHashMap<String, Object>(action);
        result.put("confirmUrl", confirmUrl(actionId));
        result.put("responseCount", responses.size());
        result.put("preview", truncate(dto.getResponseBody(), 8000));
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

    private List<ApiResponseDTO> parseResponses(JsonNode args, String defaultBody, String defaultContentType, String defaultName) {
        if (args.has("responses") && args.get("responses").isArray() && args.get("responses").size() > 0) {
            return parseResponseArray(args.get("responses"), defaultBody, defaultContentType, 0);
        }
        ApiResponseDTO single = new ApiResponseDTO();
        single.setName(defaultName);
        single.setResponseCode(200);
        single.setContentType(defaultContentType);
        single.setResponseBody(defaultBody);
        single.setBodyType("TEXT");
        single.setActive(true);
        single.setSortOrder(0);
        return new ArrayList<ApiResponseDTO>(Collections.singletonList(single));
    }

    private List<ApiResponseDTO> parseResponseArray(JsonNode array, String defaultBody, String defaultContentType, int offset) {
        List<ApiResponseDTO> result = new ArrayList<ApiResponseDTO>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode item = array.get(i);
            if (!item.isObject()) {
                throw new BizException(40001, "responses[" + i + "] 必须是对象");
            }
            ApiResponseDTO response = new ApiResponseDTO();
            response.setName(text(item, "name").isEmpty() ? "响应体 " + (offset + i + 1) : text(item, "name"));
            response.setResponseCode(item.path("responseCode").asInt(200));
            response.setContentType(text(item, "contentType").isEmpty() ? defaultContentType : text(item, "contentType"));
            response.setResponseBody(text(item, "responseBody").isEmpty() ? defaultBody : text(item, "responseBody"));
            response.setBodyType(text(item, "bodyType").isEmpty() ? "TEXT" : text(item, "bodyType").toUpperCase());
            response.setActive(!item.has("active") || item.get("active").asBoolean(true));
            response.setSortOrder(item.has("sortOrder") ? item.get("sortOrder").asInt(offset + i) : offset + i);
            response.setDelayMs(item.path("delayMs").asInt(0));
            JsonNode conditions = item.get("conditions");
            if (conditions != null && !conditions.isNull()) {
                response.setConditions(conditions.isTextual() ? conditions.asText() : conditions.toString());
            }
            result.add(response);
        }
        boolean hasActive = false;
        for (ApiResponseDTO response : result) {
            if (response.isActive()) {
                hasActive = true;
                break;
            }
        }
        if (!hasActive && !result.isEmpty()) {
            result.get(0).setActive(true);
        }
        return result;
    }

    private List<ApiResponseDTO> existingResponseDtos(ApiDefinitionDetailVO detail) {
        List<ApiResponseDTO> result = new ArrayList<ApiResponseDTO>();
        if (detail.getResponses() == null) {
            return result;
        }
        for (ApiResponse response : detail.getResponses()) {
            ApiResponseDTO dto = new ApiResponseDTO();
            dto.setId(response.getId());
            dto.setSoapOperationName(response.getSoapOperationName());
            dto.setName(response.getName());
            dto.setResponseCode(response.getResponseCode());
            dto.setContentType(response.getContentType());
            dto.setResponseBody(response.getResponseBody());
            dto.setBodyType(response.getBodyType());
            dto.setFileName(response.getFileName());
            dto.setFilePath(response.getFilePath());
            dto.setDownloadName(response.getDownloadName());
            dto.setFileSize(response.getFileSize());
            dto.setDelayMs(response.getDelayMs());
            dto.setActive(response.isActive());
            dto.setSortOrder(response.getSortOrder());
            dto.setConditions(response.getConditions());
            result.add(dto);
        }
        return result;
    }

    private void validateResponses(List<ApiResponseDTO> responses, String responseMode) {
        Map<String, String> modes = new LinkedHashMap<String, String>();
        modes.put("__REST__", responseMode);
        ResponseValidator.validateDtos(responses, modes);
    }

    private String normalizeResponseMode(String mode) {
        return "RANDOM".equalsIgnoreCase(mode) ? "RANDOM" : "CONDITION";
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