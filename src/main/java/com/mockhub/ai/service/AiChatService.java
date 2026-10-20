package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiChatMessage;
import com.mockhub.ai.model.dto.AiChatRequest;
import com.mockhub.ai.model.dto.AiChatResponse;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.model.dto.ApiResponseDTO;
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
    private static final String INTENT_HELP = "HELP";

    private final AiSettingsService settingsService;
    private final OpenAiCompatibleProvider provider;
    private final MockAiGenerator mockGenerator;
    private final ApiService apiService;
    private final StatsService statsService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    public AiChatService(AiSettingsService settingsService,
                         OpenAiCompatibleProvider provider,
                         MockAiGenerator mockGenerator,
                         ApiService apiService,
                         StatsService statsService,
                         PendingActionService pendingActionService,
                         ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.provider = provider;
        this.mockGenerator = mockGenerator;
        this.apiService = apiService;
        this.statsService = statsService;
        this.pendingActionService = pendingActionService;
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
        if (teamId.isEmpty() && !SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40001, "请先选择团队");
        }
        AiSettings settings = settingsService.assertUsable(null, teamId.isEmpty() ? null : teamId);
        if (teamId.isEmpty()) {
            request.setTeamId(null);
        }

        ChatPlan plan;
        if (AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            plan = mockPlan(request.getMessage());
        } else {
            plan = modelPlan(settings, request);
        }

        AiChatResponse response = new AiChatResponse();
        response.setProvider(settings.getProvider());
        response.setModel(settings.getModel());

        if (INTENT_STATS.equals(plan.intent)) {
            handleStats(request, response);
        } else if (INTENT_SEARCH.equals(plan.intent)) {
            handleSearch(request, response, plan.keyword);
        } else if (INTENT_CREATE.equals(plan.intent)) {
            handleCreate(request, settings, response, plan);
        } else {
            response.setReply(plan.reply != null && !plan.reply.trim().isEmpty()
                    ? plan.reply : helpText());
        }
        return response;
    }

    // ===== 意图执行 =====

    private void handleStats(AiChatRequest request, AiChatResponse response) {
        StatsOverviewVO overview = statsService.getOverview(request.getTeamId(), 7);
        List<TopApiVO> top = statsService.getTopApis(request.getTeamId(), 7, 3);

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (TopApiVO api : top) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("apiId", api.getApiId());
            item.put("method", api.getMethod());
            item.put("path", api.getPath());
            item.put("name", api.getName());
            item.put("count", api.getCallCount());
            items.add(item);
        }

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "STATS");
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

        ApiDefinitionDTO dto = new ApiDefinitionDTO();
        dto.setTeamId(request.getTeamId());
        dto.setType("REST");
        dto.setName(name);
        dto.setMethod(method);
        dto.setPath(path);
        dto.setResponseCode(200);
        dto.setContentType("application/json");
        dto.setResponseBody(body);
        dto.setEnabled(true);
        dto.setResponseMode("CONDITION");
        dto.setDelayMs(0);

        ApiResponseDTO first = new ApiResponseDTO();
        first.setName("成功响应");
        first.setResponseCode(200);
        first.setContentType("application/json");
        first.setResponseBody(body);
        first.setBodyType("TEXT");
        first.setActive(true);
        first.setSortOrder(0);
        dto.setResponses(Collections.singletonList(first));

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "PENDING_ACTION");
        card.put("actionType", "CREATE_API");
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("method", method);
        summary.put("path", path);
        summary.put("name", name);
        summary.put("teamId", request.getTeamId());
        card.put("summary", summary);
        card.put("preview", body.length() > 1200 ? body.substring(0, 1200) + "…" : body);

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

    // ===== 意图识别 =====

    private ChatPlan mockPlan(String message) {
        ChatPlan plan = new ChatPlan();
        String lower = message.toLowerCase();
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

    private ChatPlan modelPlan(AiSettings settings, AiChatRequest request) {
        String system = "你是 MockHub 的 AI 助手。判断用户意图，只输出 JSON："
                + "{\"intent\":\"SEARCH_APIS|API_STATS|CREATE_API_DRAFT|HELP\","
                + "\"keyword\":\"搜索关键词\",\"method\":\"GET\",\"path\":\"/api/xxx\","
                + "\"name\":\"接口名称\",\"responseBody\":\"响应体字符串\",\"reply\":\"中文回答\"}。"
                + "只有用户明确要求创建接口时才用 CREATE_API_DRAFT；不确定时用 HELP。"
                + "SEARCH_APIS 时 keyword 必填；CREATE_API_DRAFT 时 method/path 必填，responseBody 为合法 JSON 字符串。"
                + "不要输出 Markdown 或额外文本。";

        StringBuilder user = new StringBuilder();
        user.append("当前团队 ID：").append(request.getTeamId()).append("\n");
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
                    || INTENT_CREATE.equals(intent) || INTENT_HELP.equals(intent)) {
                plan.intent = intent;
            }
            plan.keyword = node.path("keyword").asText("");
            plan.method = node.path("method").asText("");
            plan.path = node.path("path").asText("");
            plan.name = node.path("name").asText("");
            JsonNode bodyNode = node.path("responseBody");
            plan.responseBody = bodyNode.isObject() || bodyNode.isArray()
                    ? bodyNode.toString() : bodyNode.asText("");
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
        private String keyword;
        private String method;
        private String path;
        private String name;
        private String responseBody;
        private String reply;
    }
}