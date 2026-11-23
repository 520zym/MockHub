package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiApiUpdateDraft;
import com.mockhub.ai.model.dto.AiChatMessage;
import com.mockhub.ai.model.dto.AiChatRequest;
import com.mockhub.ai.model.dto.AiChatResponse;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiDefinitionDetailVO;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.service.ApiService;
import com.mockhub.stats.model.dto.StatsOverviewVO;
import com.mockhub.stats.model.dto.TopApiVO;
import com.mockhub.stats.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话助手：把自然语言转成安全的内部动作。
 * <p>
 * 设计原则：模型只输出「意图 + 参数」的 JSON，服务端再调用现有业务服务执行；
 * 写操作只创建 pending action，绝不直接落库。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\b");
    private static final Pattern PATH_PATTERN = Pattern.compile("(/[A-Za-z0-9_\\-{}/.]+)");

    private static final String INTENT_SEARCH = "SEARCH_APIS";
    private static final String INTENT_STATS = "API_STATS";
    private static final String INTENT_CREATE = "CREATE_API_DRAFT";
    private static final String INTENT_UPDATE = "UPDATE_API_DRAFT";
    private static final String INTENT_HELP = "HELP";

    private final AiSettingsService settingsService;
    private final OpenAiCompatibleProvider provider;
    private final MockAiGenerator mockGenerator;
    private final AiService aiService;
    private final ApiService apiService;
    private final StatsService statsService;
    private final PendingActionService pendingActionService;
    private final AiConversationService conversationService;
    private final ObjectMapper objectMapper;

    public AiChatService(AiSettingsService settingsService,
                         OpenAiCompatibleProvider provider,
                         MockAiGenerator mockGenerator,
                         AiService aiService,
                         ApiService apiService,
                         StatsService statsService,
                         PendingActionService pendingActionService,
                         AiConversationService conversationService,
                         ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.provider = provider;
        this.mockGenerator = mockGenerator;
        this.aiService = aiService;
        this.apiService = apiService;
        this.statsService = statsService;
        this.pendingActionService = pendingActionService;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    public AiChatResponse chat(AiChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new BizException(40001, "请输入问题");
        }
        if (request.getMessage().length() > 2000) {
            throw new BizException(40001, "问题过长，请精简到 2000 字以内");
        }
        String teamId = request.getTeamId() == null ? "" : request.getTeamId().trim();
        if (teamId.isEmpty()) {
            throw new BizException(40001, "请先选择团队");
        }
        request.setTeamId(teamId);
        conversationService.validateContext(request.getConversationId(), SecurityContextUtil.getCurrentUserId(), teamId);
        if (request.getContextApiId() != null && !request.getContextApiId().trim().isEmpty()) {
            request.setContextApiId(request.getContextApiId().trim());
            requireScopedApi(teamId, request.getContextApiId());
        }
        AiSettings settings = settingsService.assertUsable(null, teamId);

        log.info("AI 对话请求：teamId={}, conversationId={}, contextApiId={}, messageChars={}",
                request.getTeamId(), request.getConversationId(), request.getContextApiId(),
                request.getMessage().length());

        ChatPlan plan;
        if (AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            plan = mockPlan(request.getMessage(), request.getContextApiId());
        } else {
            plan = modelPlan(settings, request);
        }

        log.info("AI 对话意图：intent={}, path={}, apiId={}", plan.intent, plan.path, plan.apiId);

        AiChatResponse response = new AiChatResponse();
        response.setProvider(settings.getProvider());
        response.setModel(settings.getModel());

        if (INTENT_STATS.equals(plan.intent)) {
            handleStats(request, response);
        } else if (INTENT_SEARCH.equals(plan.intent)) {
            handleSearch(request, response, plan.keyword);
        } else if (INTENT_CREATE.equals(plan.intent)) {
            handleCreate(request, settings, response, plan);
        } else if (INTENT_UPDATE.equals(plan.intent)) {
            handleUpdate(request, settings, response, plan);
        } else {
            response.setReply(plan.reply != null && !plan.reply.trim().isEmpty()
                    ? plan.reply : helpText());
        }

        boolean newConversation = request.getConversationId() == null || request.getConversationId().trim().isEmpty();
        AiConversation conversation = conversationService.appendExchange(
                request.getConversationId(), SecurityContextUtil.getCurrentUserId(), request.getTeamId(),
                request.getMessage(), response.getReply(), response.getCards(),
                response.getProvider(), response.getModel());
        if (newConversation && conversation != null) {
            String title = generateConversationTitle(settings, plan, request, response);
            if (title != null && !title.trim().isEmpty()) {
                try {
                    conversationService.rename(conversation.getId(), SecurityContextUtil.getCurrentUserId(), title);
                    conversation.setTitle(title);
                } catch (Exception e) {
                    log.warn("AI 会话标题保存失败，保留默认标题：{}", e.getMessage());
                }
            }
        }
        response.setConversationId(conversation.getId());
        response.setConversationTitle(conversation.getTitle());
        return response;
    }

    /**
     * 新建会话时生成短标题：优先让模型概括，失败或演示模式使用本地意图摘要。
     */
    private String generateConversationTitle(AiSettings settings, ChatPlan plan, AiChatRequest request, AiChatResponse response) {
        String fallback = fallbackTitle(plan, request);
        if (settings == null || AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            return fallback;
        }
        try {
            String prompt = "用户：" + clip(request.getMessage(), 700)
                    + "\n助手回复：" + clip(response.getReply(), 700);
            String raw = provider.complete(settings,
                    "你是会话标题生成器。根据用户问题和助手回复，用不超过14个汉字概括一个具体主题。"
                            + "只输出标题本身，不要引号、标点、序号、前缀或解释。",
                    prompt);
            String title = cleanGeneratedTitle(raw);
            return title.isEmpty() ? fallback : title;
        } catch (Exception e) {
            log.warn("AI 会话标题生成失败，使用本地摘要：{}", e.getMessage());
            return fallback;
        }
    }

    private String fallbackTitle(ChatPlan plan, AiChatRequest request) {
        if (plan != null) {
            if (INTENT_STATS.equals(plan.intent)) return "接口调用排行";
            if (INTENT_SEARCH.equals(plan.intent)) {
                String keyword = normalizeShort(plan.keyword, 16);
                return keyword.isEmpty() ? "查找接口" : "查找接口：" + keyword;
            }
            if (INTENT_CREATE.equals(plan.intent)) return "创建 Mock 接口";
            if (INTENT_UPDATE.equals(plan.intent)) return "修改接口配置";
        }
        String text = request == null || request.getMessage() == null ? "" : request.getMessage();
        text = text.replaceAll("\\s+", " ").trim();
        for (int i = 0; i < 2; i++) {
            text = text.replaceFirst("^(请帮我|帮我看看|帮我|请|麻烦|我想|想要|能不能|可以|查一下|看一下|找一下|最近)\\s*", "");
        }
        text = text.replaceAll("[。！？!?；;：:，,]+$", "").trim();
        if (text.isEmpty()) return "新会话";
        return text.length() <= 20 ? text : text.substring(0, 20) + "…";
    }

    private String cleanGeneratedTitle(String raw) {
        String value = raw == null ? "" : raw.trim();
        int newline = value.indexOf('\n');
        if (newline >= 0) value = value.substring(0, newline).trim();
        value = value.replaceFirst("^(标题|会话标题)[：:]\\s*", "");
        value = value.replaceAll("^[“”\"'《》]+|[“”\"'《》]+$", "");
        value = value.replaceAll("[。！？!?；;：:，,]+$", "").trim();
        if (value.length() > 24) value = value.substring(0, 24) + "…";
        return value;
    }

    private String normalizeShort(String value, int max) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String clip(String value, int max) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    // ===== 意图执行 =====

    private void handleStats(AiChatRequest request, AiChatResponse response) {
        StatsOverviewVO overview = statsService.getOverview(request.getTeamId(), 7);
        List<TopApiVO> top = statsService.getTopApis(request.getTeamId(), 7, 3);

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (TopApiVO api : top) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("apiId", api.getApiId());
            item.put("teamId", request.getTeamId());
            item.put("method", api.getMethod());
            item.put("path", api.getPath());
            item.put("name", api.getName());
            item.put("count", api.getCallCount());
            items.add(item);
        }

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "STATS");
        card.put("teamId", request.getTeamId());
        card.put("title", "近 7 天调用排行");
        card.put("range", "近 7 天");
        card.put("total", overview.getWindowCallCount());
        card.put("today", overview.getTodayCallCount());
        card.put("items", items);
        response.getCards().add(card);

        if (top.isEmpty()) {
            response.setReply("近 7 天还没有请求记录。可以先在接口详情里复制 Mock 地址调一次。");
        } else {
            TopApiVO first = top.get(0);
            response.setReply("近 7 天共 " + overview.getWindowCallCount() + " 次调用，最多的是 "
                    + first.getMethod() + " " + first.getPath() + "，共 " + first.getCallCount() + " 次。");
        }
    }

    private void handleSearch(AiChatRequest request, AiChatResponse response, String keyword) {
        String searchKeyword = keyword == null ? "" : keyword.trim();
        PageResult<ApiDefinitionVO> page = apiService.list(request.getTeamId(), null, null, null,
                searchKeyword, null, null, null, null, 1, 5);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (ApiDefinitionVO api : page.getItems()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("apiId", api.getId());
            item.put("method", api.getMethod());
            item.put("path", api.getPath());
            item.put("name", api.getName());
            item.put("teamId", api.getTeamId());
            items.add(item);
        }
        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "API_LIST");
        card.put("teamId", request.getTeamId());
        card.put("title", "找到 " + page.getTotal() + " 个接口");
        card.put("keyword", searchKeyword);
        card.put("items", items);
        response.getCards().add(card);
        response.setReply(items.isEmpty()
                ? "没有找到与「" + searchKeyword + "」匹配的接口，可以换个关键词，或先创建接口。"
                : "在「" + searchKeyword + "」下找到 " + page.getTotal() + " 个接口：");
    }

    private void handleCreate(AiChatRequest request, AiSettings settings,
                              AiChatResponse response, ChatPlan plan) {
        if (request.getTeamId() == null || request.getTeamId().trim().isEmpty()) {
            response.setReply("创建接口需要先选择具体团队，请在助手顶部把范围切换到一个团队后再试。");
            return;
        }
        if (!settings.isCapDrafts()) {
            response.setReply("管理员已关闭「创建接口草稿」能力，可以改用编辑器里的 AI 生成。");
            return;
        }
        String method = normalizeMethod(plan.method);
        String path = plan.path == null ? "" : plan.path.trim();
        if (path.isEmpty() && containsAny(request.getMessage(), "随便", "任意", "随你", "你定", "看着办", "你来定")) {
            path = "/api/mock-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (path.isEmpty()) {
            response.setReply("请给我接口路径，例如：帮我创建 GET /api/user/info 的 Mock，返回姓名、头像、等级。");
            return;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String conflict = apiService.findConflictingApiName(request.getTeamId(), method, path, null);
        if (conflict != null) {
            response.setReply("该团队已存在相同路径与方法的接口「" + conflict + "」，请换一个路径或直接编辑已有接口。");
            return;
        }

        String name = plan.name == null || plan.name.trim().isEmpty()
                ? defaultName(path) : plan.name.trim();
        name = name.length() > 60 ? name.substring(0, 60) : name;

        String body = plan.responseBody;
        if (body == null || body.trim().isEmpty()) {
            body = mockGenerator.response(buildGenerationContext(request, name, method, path)).getContent();
        }
        body = sanitizeJson(body);
        List<ApiResponseDTO> responses = buildResponses(plan, body);
        String defaultBody = body;
        for (ApiResponseDTO item : responses) {
            if (item.getConditions() == null || item.getConditions().trim().isEmpty()) {
                defaultBody = item.getResponseBody();
                break;
            }
        }

        ApiDefinitionDTO dto = new ApiDefinitionDTO();
        dto.setTeamId(request.getTeamId());
        dto.setType("REST");
        dto.setName(name);
        dto.setMethod(method);
        dto.setPath(path);
        dto.setResponseCode(200);
        dto.setContentType("application/json");
        dto.setResponseBody(defaultBody);
        dto.setEnabled(true);
        dto.setResponseMode("CONDITION");
        dto.setDelayMs(0);
        dto.setResponses(responses);

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "PENDING_ACTION");
        card.put("actionType", "CREATE_API");
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("method", method);
        summary.put("path", path);
        summary.put("name", name);
        summary.put("teamId", request.getTeamId());
        card.put("summary", summary);
        card.put("preview", defaultBody.length() > 8000 ? defaultBody.substring(0, 8000) + "…" : defaultBody);
        card.put("responsePreviews", responsePreviews(responses));

        try {
            Map<String, Object> created = pendingActionService.createApiDraft(dto, method + " " + path);
            card.put("actionId", created.get("actionId"));
            card.put("status", created.get("status"));
            card.put("expiresAt", created.get("expiresAt"));
        } catch (BizException e) {
            throw e;
        }
        response.getCards().add(card);
        response.setReply("已生成接口草稿，确认后才会真正创建：");
    }

    private List<ApiResponseDTO> buildResponses(ChatPlan plan, String fallbackBody) {
        List<ApiResponseDTO> responses = new ArrayList<ApiResponseDTO>();
        if (plan.responses != null && !plan.responses.isEmpty()) {
            int index = 0;
            for (PlannedResponse planned : plan.responses) {
                String responseBody = planned.responseBody == null || planned.responseBody.trim().isEmpty()
                        ? fallbackBody : sanitizeJson(planned.responseBody);
                String conditions = conditionsJson(planned.conditions);
                ApiResponseDTO response = new ApiResponseDTO();
                response.setName(responseName(planned.name, index));
                response.setResponseCode(200);
                response.setContentType("application/json");
                response.setResponseBody(responseBody);
                response.setBodyType("TEXT");
                response.setActive(true);
                response.setSortOrder(index);
                response.setConditions(conditions);
                responses.add(response);
                index++;
            }
        }
        boolean hasDefault = false;
        for (ApiResponseDTO response : responses) {
            if (response.getConditions() == null || response.getConditions().trim().isEmpty()) {
                hasDefault = true;
                break;
            }
        }
        if (responses.isEmpty()) {
            ApiResponseDTO response = new ApiResponseDTO();
            response.setName("默认响应");
            response.setResponseCode(200);
            response.setContentType("application/json");
            response.setResponseBody(fallbackBody);
            response.setBodyType("TEXT");
            response.setActive(true);
            response.setSortOrder(0);
            responses.add(response);
        } else if (!hasDefault) {
            ApiResponseDTO response = new ApiResponseDTO();
            response.setName("默认响应");
            response.setResponseCode(200);
            response.setContentType("application/json");
            response.setResponseBody(fallbackBody);
            response.setBodyType("TEXT");
            response.setActive(true);
            response.setSortOrder(responses.size());
            responses.add(response);
        }
        return responses;
    }

    private String responseName(String name, int index) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "返回体 " + (index + 1);
        return value.length() <= 60 ? value : value.substring(0, 60);
    }

    private String conditionsJson(JsonNode conditions) {
        if (conditions == null || !conditions.isArray() || conditions.size() == 0) {
            return null;
        }
        List<Map<String, String>> normalized = new ArrayList<Map<String, String>>();
        for (JsonNode condition : conditions) {
            if (condition == null || !condition.isObject()) continue;
            String path = condition.path("path").asText("").trim();
            if (path.isEmpty()) continue;
            String source = condition.path("source").asText("BODY").trim().toUpperCase();
            if (!("BODY".equals(source) || "QUERY".equals(source) || "HEADER".equals(source))) source = "BODY";
            String operator = condition.path("operator").asText("EQ").trim().toUpperCase();
            String valueType = condition.path("valueType").asText("STRING").trim().toUpperCase();
            if (!("STRING".equals(valueType) || "NUMBER".equals(valueType) || "BOOLEAN".equals(valueType))) valueType = "STRING";
            JsonNode valueNode = condition.path("value");
            String value = valueNode.isObject() || valueNode.isArray() ? valueNode.toString() : valueNode.asText("");
            Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("source", source);
            item.put("path", path.length() > 200 ? path.substring(0, 200) : path);
            item.put("operator", operator);
            item.put("value", value.length() > 500 ? value.substring(0, 500) : value);
            item.put("valueType", valueType);
            normalized.add(item);
        }
        if (normalized.isEmpty()) return null;
        Map<String, Object> rule = new LinkedHashMap<String, Object>();
        rule.put("conditions", normalized);
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (Exception e) {
            log.warn("生成返回体匹配条件失败：{}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> responsePreviews(List<ApiResponseDTO> responses) {
        List<Map<String, Object>> previews = new ArrayList<Map<String, Object>>();
        for (ApiResponseDTO response : responses) {
            Map<String, Object> preview = new LinkedHashMap<String, Object>();
            preview.put("name", response.getName());
            preview.put("responseBody", response.getResponseBody());
            preview.put("conditions", response.getConditions());
            preview.put("isDefault", response.getConditions() == null || response.getConditions().trim().isEmpty());
            previews.add(preview);
        }
        return previews;
    }

    private void handleUpdate(AiChatRequest request, AiSettings settings,
                              AiChatResponse response, ChatPlan plan) {
        if (!settings.isCapDrafts()) {
            response.setReply("管理员已关闭「创建 / 修改接口草稿」能力，暂时不能通过对话修改接口。");
            return;
        }
        if (!settings.isCapResponseBody()) {
            response.setReply("管理员已关闭「生成响应体」能力，暂时不能用 AI 修改返回值。");
            return;
        }
        ApiDefinitionDetailVO detail = resolveUpdateTarget(request, plan);
        if (detail == null) {
            response.setReply("请告诉我要修改哪个接口，例如：把 GET /api/user/info 的返回值数组弄大一些；如果刚创建过接口，也可以直接说「把返回值数组弄大一些」。");
            return;
        }
        ApiResponse target = selectResponse(detail, plan);
        if (target == null) {
            response.setReply("接口「" + detail.getName() + "」没有可修改的文本返回体。文件类型返回体请到接口编辑页处理。");
            return;
        }
        if (!"TEXT".equalsIgnoreCase(target.getBodyType())) {
            response.setReply("返回体「" + target.getName() + "」是文件类型，暂不支持通过对话修改。");
            return;
        }
        String oldBody = target.getResponseBody() == null ? "" : target.getResponseBody();
        AiGenerateRequest gen = new AiGenerateRequest();
        gen.setTeamId(detail.getTeamId());
        gen.setApiType(detail.getType());
        gen.setApiName(detail.getName());
        gen.setMethod(detail.getMethod());
        gen.setPath(detail.getPath());
        gen.setResponseName(target.getName());
        gen.setContentType(target.getContentType());
        gen.setExistingBody(oldBody);
        gen.setInstruction(request.getMessage() + "\n保持原有结构和字段，只修改用户要求变化的部分。");
        gen.setMode("COMPLETE");
        gen.setSize("STANDARD");

        AiResponseResult generated = aiService.generateResponse(gen);
        String newBody = generated.getContent();
        if (AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            newBody = expandArrayForUpdate(newBody, request.getMessage());
        }
        if (newBody == null || newBody.trim().isEmpty()) {
            response.setReply("这次没有生成可用的返回体，请把要改的字段说得更具体一些。");
            return;
        }
        if (newBody.equals(oldBody)) {
            response.setReply("模型没有产生实际变化，请说明要改哪个字段或数组，例如「把 data 数组补到 5 条」。");
            return;
        }

        AiApiUpdateDraft draft = new AiApiUpdateDraft();
        draft.setApiId(detail.getId());
        draft.setBaseRevision(detail.getRevision());
        draft.setResponseId(target.getId());
        draft.setTeamId(detail.getTeamId());
        draft.setApiName(detail.getName());
        draft.setMethod(detail.getMethod());
        draft.setPath(detail.getPath());
        draft.setResponseName(target.getName());
        draft.setOldBody(oldBody);
        draft.setNewBody(newBody);

        Map<String, Object> card = pendingActionService.createApiUpdateDraft(
                draft, detail.getMethod() + " " + detail.getPath() + " · " + target.getName());
        response.getCards().add(card);
        response.setReply("已生成接口修改草稿，确认后才会更新「" + detail.getName() + "」：");
    }

    private ApiDefinitionDetailVO requireScopedApi(String teamId, String apiId) {
        ApiDefinitionDetailVO detail = apiService.getById(apiId);
        if (detail == null || teamId == null || !teamId.equals(detail.getTeamId())) {
            throw new BizException(40102, "接口不属于当前团队，请重新选择上下文接口");
        }
        return detail;
    }

    private ApiDefinitionDetailVO resolveUpdateTarget(AiChatRequest request, ChatPlan plan) {
        String contextId = firstNonBlank(plan.apiId, request.getContextApiId());
        if (contextId != null) {
            // An explicit or model-selected ID must never escape the selected workspace.
            // Do not swallow a scope error and silently select a different API by path.
            return requireScopedApi(request.getTeamId(), contextId);
        }
        String path = firstNonBlank(plan.path, extractPath(request.getMessage()));
        if (path == null) {
            return null;
        }
        String method = plan.method == null || plan.method.trim().isEmpty() ? null : normalizeMethod(plan.method);
        // 模型可能把方法猜错，路径命中优先；方法只作为偏好。
        PageResult<ApiDefinitionVO> page = apiService.list(request.getTeamId(), null, null, null,
                path, null, null, null, null, 1, 20);
        ApiDefinitionVO matched = null;
        for (ApiDefinitionVO api : page.getItems()) {
            if (path.equals(api.getPath())) {
                if (method == null || method.equals(api.getMethod())) {
                    return requireScopedApi(request.getTeamId(), api.getId());
                }
                if (matched == null) {
                    matched = api;
                }
            }
        }
        return matched == null ? null : requireScopedApi(request.getTeamId(), matched.getId());
    }

    private ApiResponse selectResponse(ApiDefinitionDetailVO detail, ChatPlan plan) {
        List<ApiResponse> responses = detail.getResponses();
        if (responses == null || responses.isEmpty()) {
            return null;
        }
        if (plan.responseName != null && !plan.responseName.trim().isEmpty()) {
            for (ApiResponse response : responses) {
                if (plan.responseName.trim().equals(response.getName())) {
                    return response;
                }
            }
        }
        for (ApiResponse response : responses) {
            if (response.isActive() && "TEXT".equalsIgnoreCase(response.getBodyType())) {
                return response;
            }
        }
        for (ApiResponse response : responses) {
            if ("TEXT".equalsIgnoreCase(response.getBodyType())) {
                return response;
            }
        }
        return null;
    }

    private String expandArrayForUpdate(String body, String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (!containsAny(lower, "数组", "列表", "多条", "大一些", "多一点", "扩充", "扩展", "丰富")) {
            return body;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            boolean changed = expandArrays(root, 0);
            if (!changed) {
                return body;
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return body;
        }
    }

    private boolean expandArrays(JsonNode node, int depth) {
        if (node == null || depth > 5) {
            return false;
        }
        boolean changed = false;
        if (node.isObject()) {
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                JsonNode child = fields.next().getValue();
                if (child.isArray() && child.size() > 0 && child.size() < 4) {
                    ArrayNode array = (ArrayNode) child;
                    while (array.size() < 4) {
                        array.add(array.get(0).deepCopy());
                    }
                    changed = true;
                } else if (child.isObject() || child.isArray()) {
                    changed = expandArrays(child, depth + 1) || changed;
                }
            }
        } else if (node.isArray() && node.size() > 0 && node.size() < 4) {
            ArrayNode array = (ArrayNode) node;
            while (array.size() < 4) {
                array.add(array.get(0).deepCopy());
            }
            changed = true;
        }
        return changed;
    }

    private String extractPath(String message) {
        Matcher matcher = PATH_PATTERN.matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        if (second != null && !second.trim().isEmpty()) return second.trim();
        return null;
    }

    // ===== 意图识别 =====

    private ChatPlan mockPlan(String message, String contextApiId) {
        ChatPlan plan = new ChatPlan();
        String lower = message.toLowerCase();
        if (containsAny(lower, "修改", "改成", "改为", "更新", "调整", "把返回", "返回值", "响应体", "数组", "大一些", "多条", "扩充", "扩展", "丰富")
                && !containsAny(lower, "创建", "新建")) {
            plan.intent = INTENT_UPDATE;
            plan.apiId = contextApiId;
            Matcher pathMatcher = PATH_PATTERN.matcher(message);
            if (pathMatcher.find()) {
                plan.path = pathMatcher.group(1);
            }
            return plan;
        }
        if (containsAny(lower, "统计", "调用次数", "调用量", "最多", "热门", "排行")) {
            plan.intent = INTENT_STATS;
            return plan;
        }
        if (containsAny(lower, "创建", "新建", "建一个", "加一个", "生成一个接口", "创建一个")) {
            plan.intent = INTENT_CREATE;
            Matcher methodMatcher = METHOD_PATTERN.matcher(message.toUpperCase());
            plan.method = methodMatcher.find() ? methodMatcher.group(1) : "GET";
            Matcher pathMatcher = PATH_PATTERN.matcher(message);
            if (pathMatcher.find()) {
                plan.path = pathMatcher.group(1);
            }
            plan.name = defaultName(plan.path == null ? "" : plan.path);
            if (wantsMultiResponses(message)) plan.responses = mockMultiResponses();
            return plan;
        }
        if (containsAny(lower, "搜索", "找", "查一下", "哪个接口", "有没有", "在哪")) {
            plan.intent = INTENT_SEARCH;
            plan.keyword = guessKeyword(message);
            return plan;
        }
        plan.intent = INTENT_HELP;
        plan.reply = helpText();
        return plan;
    }

    private boolean wantsMultiResponses(String message) {
        String text = message == null ? "" : message;
        if (text.contains("多个返回体") || text.contains("多返回体") || text.contains("多个响应")) return true;
        return text.contains("返回体") && text.matches("(?s).*(3个|三个|4个|四个|多个|每个.*条件|不同.*条件|默认).*");
    }

    private List<PlannedResponse> mockMultiResponses() {
        List<PlannedResponse> list = new ArrayList<PlannedResponse>();
        list.add(plannedResponse("条件 A", "{\"case\":\"A\"}", mockCondition("BODY", "type", "A")));
        list.add(plannedResponse("条件 B", "{\"case\":\"B\"}", mockCondition("QUERY", "status", "active")));
        list.add(plannedResponse("条件 C", "{\"case\":\"C\"}", mockCondition("HEADER", "X-Mock", "yes")));
        list.add(plannedResponse("默认响应", "{\"code\":0,\"message\":\"default\"}", null));
        return list;
    }

    private PlannedResponse plannedResponse(String name, String body, JsonNode conditions) {
        PlannedResponse response = new PlannedResponse();
        response.name = name;
        response.responseBody = body;
        response.conditions = conditions;
        return response;
    }

    private JsonNode mockCondition(String source, String path, String value) {
        try {
            return objectMapper.readTree("[{\"source\":\"" + source + "\",\"path\":\"" + path
                    + "\",\"operator\":\"EQ\",\"value\":\"" + value + "\",\"valueType\":\"STRING\"}]");
        } catch (Exception e) {
            return null;
        }
    }

    private ChatPlan modelPlan(AiSettings settings, AiChatRequest request) {
        String system = "你是 MockHub 的 AI 助手。判断用户意图，只输出 JSON："
                + "{\"intent\":\"SEARCH_APIS|API_STATS|CREATE_API_DRAFT|UPDATE_API_DRAFT|HELP\","
                + "\"apiId\":\"要修改的接口 ID\",\"keyword\":\"搜索关键词\",\"method\":\"GET\",\"path\":\"/api/xxx\","
                + "\"name\":\"接口名称\",\"responseName\":\"要修改的返回体名称\","
                + "\"responseBody\":\"单个响应体字符串\",\"reply\":\"中文回答\","
                + "\"responses\":[{\"name\":\"返回体名称\",\"responseBody\":{},\"active\":true,"
                + "\"conditions\":[{\"source\":\"BODY|QUERY|HEADER\",\"path\":\"字段路径\","
                + "\"operator\":\"EQ|NE|CONTAINS|IN|GT|GTE|LT|LTE|REGEX|IS_EMPTY\",\"value\":\"匹配值\","
                + "\"valueType\":\"STRING|NUMBER|BOOLEAN\"}]}]}。"
                + "只有用户明确要求创建接口时才用 CREATE_API_DRAFT；要求修改已有接口或其返回值/响应体时用 UPDATE_API_DRAFT；不确定时用 HELP。"
                + "UPDATE_API_DRAFT 优先使用上下文接口 ID；没有上下文时尽量从用户话里提取 path。创建与修改不要混淆。"
                + "SEARCH_APIS 时 keyword 必填；CREATE_API_DRAFT 时 method/path 必填。"
                + "CREATE_API_DRAFT 下：用户没有给 path/name 且表示“随便/你定”时，由你生成合理的 path 和 name；"
                + "用户只要求一个返回体时用 responseBody；用户要求多个返回体时，必须输出 responses 数组，"
                + "每个条件返回体写不同 conditions，并且恰好保留一个 conditions 为空的默认返回体。"
                + "用户没有指定具体条件或响应内容时，由你合理设计字段、条件值和 JSON 响应体；不要追问，直接生成草稿。"
                + "conditions 是 AND 关系；responseBody 必须是合法 JSON。不要输出 Markdown 或额外文本。";

        StringBuilder user = new StringBuilder();
        user.append("当前团队 ID：").append(request.getTeamId()).append("\n");
        if (request.getContextApiId() != null && !request.getContextApiId().trim().isEmpty()) {
            user.append("当前上下文接口 ID：").append(request.getContextApiId()).append("\n");
        }
        List<AiChatMessage> history = request.getHistory();
        if (history != null && !history.isEmpty()) {
            user.append("最近对话：\n");
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                AiChatMessage message = history.get(i);
                String content = message.getContent() == null ? "" : message.getContent();
                user.append("- ").append(message.getRole()).append("：")
                        .append(content.length() > 300 ? content.substring(0, 300) + "…" : content)
                        .append("\n");
            }
        }
        user.append("当前问题：").append(request.getMessage());

        String raw = provider.complete(settings, system, user.toString());
        return parsePlan(raw);
    }

    private ChatPlan parsePlan(String raw) {
        ChatPlan plan = new ChatPlan();
        plan.intent = INTENT_HELP;
        plan.reply = raw == null ? "" : raw.trim();
        if (raw == null) {
            return plan;
        }
        try {
            String json = raw.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return plan;
            }
            JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
            String intent = node.path("intent").asText(INTENT_HELP).toUpperCase();
            if (INTENT_SEARCH.equals(intent) || INTENT_STATS.equals(intent)
                    || INTENT_CREATE.equals(intent) || INTENT_UPDATE.equals(intent)
                    || INTENT_HELP.equals(intent)) {
                plan.intent = intent;
            }
            plan.apiId = node.path("apiId").asText("");
            plan.keyword = node.path("keyword").asText("");
            plan.method = node.path("method").asText("");
            plan.path = node.path("path").asText("");
            plan.name = node.path("name").asText("");
            plan.responseName = node.path("responseName").asText("");
            JsonNode bodyNode = node.path("responseBody");
            plan.responseBody = bodyNode.isObject() || bodyNode.isArray()
                    ? bodyNode.toString() : bodyNode.asText("");
            JsonNode responsesNode = node.path("responses");
            if (responsesNode.isArray()) {
                for (JsonNode responseNode : responsesNode) {
                    if (responseNode == null || !responseNode.isObject()) continue;
                    PlannedResponse response = new PlannedResponse();
                    response.name = responseNode.path("name").asText("");
                    JsonNode responseBodyNode = responseNode.path("responseBody");
                    response.responseBody = responseBodyNode.isObject() || responseBodyNode.isArray()
                            ? responseBodyNode.toString() : responseBodyNode.asText("");
                    JsonNode conditionsNode = responseNode.path("conditions");
                    if (conditionsNode.isArray()) {
                        response.conditions = conditionsNode;
                    } else if (conditionsNode.path("conditions").isArray()) {
                        response.conditions = conditionsNode.path("conditions");
                    }
                    plan.responses.add(response);
                }
            }
            String reply = node.path("reply").asText("");
            if (!reply.trim().isEmpty()) {
                plan.reply = reply;
            }
        } catch (Exception e) {
            log.warn("解析 AI 对话计划失败，降级为文本回复：{}", e.getMessage());
        }
        return plan;
    }

    // ===== 辅助 =====

    private AiGenerateRequest buildGenerationContext(AiChatRequest request, String name, String method, String path) {
        AiGenerateRequest gen = new AiGenerateRequest();
        gen.setTeamId(request.getTeamId());
        gen.setApiType("REST");
        gen.setApiName(name);
        gen.setMethod(method);
        gen.setPath(path);
        gen.setResponseName("成功响应");
        gen.setContentType("application/json");
        gen.setInstruction(request.getMessage());
        gen.setSize("STANDARD");
        return gen;
    }

    private String sanitizeJson(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "{\n  \"code\": 0,\n  \"message\": \"success\"\n}";
        }
        try {
            objectMapper.readTree(body);
            return body;
        } catch (Exception e) {
            return "{\n  \"code\": 0,\n  \"message\": \"success\"\n}";
        }
    }

    private String normalizeMethod(String method) {
        if (method == null) {
            return "GET";
        }
        String upper = method.trim().toUpperCase();
        if (upper.equals("POST") || upper.equals("PUT") || upper.equals("DELETE")
                || upper.equals("PATCH") || upper.equals("GET")) {
            return upper;
        }
        return "GET";
    }

    private String defaultName(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.contains("user") || lower.contains("用户")) return "用户信息查询";
        if (lower.contains("order") || lower.contains("订单")) return "订单查询";
        if (lower.contains("pay") || lower.contains("支付")) return "支付接口";
        if (lower.contains("coupon") || lower.contains("优惠券")) return "优惠券列表";
        if (lower.contains("refund") || lower.contains("退款")) return "退款申请";
        return "AI 创建的接口";
    }

    private String guessKeyword(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("用户") || lower.contains("user")) return "user";
        if (lower.contains("订单") || lower.contains("order")) return "order";
        if (lower.contains("支付") || lower.contains("pay")) return "pay";
        if (lower.contains("退款") || lower.contains("refund")) return "refund";
        if (lower.contains("优惠券") || lower.contains("coupon")) return "coupon";
        String cleaned = message.replace("搜索", "").replace("查找", "")
                .replace("找一下", "").replace("帮我", "").replace("接口", "")
                .replace("一下", "").replace("的", "").trim();
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
    }

    private String helpText() {
        return "我可以帮你：\n"
                + "1. 查询近 7 天调用统计，例如「最近 7 天哪个接口调用最多？」\n"
                + "2. 搜索 Mock 接口，例如「找一下用户相关的接口」\n"
                + "3. 创建接口草稿，例如「帮我创建 GET /api/user/info 的 Mock，返回姓名、头像、等级」\n"
                + "4. 修改已有接口，例如「把 GET /api/user/info 的返回值数组弄大一些」；在编辑页直接说「把返回值数组弄大一些」即可。\n"
                + "写操作都会先生成确认卡片，确认后才会落库。";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static class ChatPlan {
        private String intent;
        private String apiId;
        private String keyword;
        private String responseName;
        private String method;
        private String path;
        private String name;
        private String responseBody;
        private String reply;
        private List<PlannedResponse> responses = new ArrayList<PlannedResponse>();
    }

    private static class PlannedResponse {
        private String name;
        private String responseBody;
        private JsonNode conditions;
    }
}