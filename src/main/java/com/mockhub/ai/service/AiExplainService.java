package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiChainItem;
import com.mockhub.ai.model.dto.AiExplainResult;
import com.mockhub.common.model.BizException;
import com.mockhub.log.model.RequestLog;
import com.mockhub.log.repository.LogRepository;
import com.mockhub.mock.model.dto.ApiDefinitionDetailVO;
import com.mockhub.mock.model.dto.match.MatchRule;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 匹配诊断：重放一次历史请求的判定链路，并给出可读解释与修正建议。
 * <p>
 * 链路本身由服务端评估（与运行时同一套操作符语义），模型只负责组织语言；
 * 模型失败时回退到服务端模板，保证诊断始终可用。
 */
@Service
public class AiExplainService {

    private static final Logger log = LoggerFactory.getLogger(AiExplainService.class);

    private final LogRepository logRepository;
    private final ApiService apiService;
    private final AiSettingsService settingsService;
    private final OpenAiCompatibleProvider provider;
    private final AiMatchEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public AiExplainService(LogRepository logRepository,
                            ApiService apiService,
                            AiSettingsService settingsService,
                            OpenAiCompatibleProvider provider,
                            AiMatchEvaluator evaluator,
                            ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.apiService = apiService;
        this.settingsService = settingsService;
        this.provider = provider;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    public AiExplainResult explain(String requestLogId) {
        if (requestLogId == null || requestLogId.trim().isEmpty()) {
            throw new BizException(40001, "缺少请求日志 ID");
        }
        RequestLog requestLog = logRepository.findRequestLogById(requestLogId);
        if (requestLog == null) {
            throw new BizException(40403, "请求日志不存在或已被清理");
        }
        ApiDefinitionDetailVO api = apiService.getById(requestLog.getApiId());
        if (api == null) {
            throw new BizException(40402, "接口不存在，可能已被删除");
        }
        AiSettings settings = settingsService.assertUsable(AiSettingsService.CAP_EXPLAIN, requestLog.getTeamId());

        List<ApiResponse> responses = new ArrayList<ApiResponse>();
        if (api.getResponses() != null) {
            for (ApiResponse response : api.getResponses()) {
                if (response.isActive()) {
                    responses.add(response);
                }
            }
        }
        Collections.sort(responses, new Comparator<ApiResponse>() {
            @Override
            public int compare(ApiResponse a, ApiResponse b) {
                return Integer.compare(a.getSortOrder(), b.getSortOrder());
            }
        });
        if (responses.isEmpty()) {
            throw new BizException(40410, "该接口没有启用的返回体");
        }

        List<AiChainItem> chain = new ArrayList<AiChainItem>();
        ApiResponse matched = null;
        String firstMissReason = null;
        String firstMissName = null;
        AiChainItem fallbackItem = null;
        ApiResponse fallback = null;
        boolean single = responses.size() == 1;

        for (int i = 0; i < responses.size(); i++) {
            ApiResponse response = responses.get(i);
            AiChainItem item = new AiChainItem();
            item.setIndex(i + 1);
            item.setResponseId(response.getId());
            item.setName(response.getName());

            if (matched != null) {
                item.setStatus("SKIP");
                item.setDetail("已命中前一条，未参与匹配");
                chain.add(item);
                continue;
            }
            if (single) {
                item.setStatus("HIT");
                item.setDetail("唯一启用返回体，直接返回");
                matched = response;
                chain.add(item);
                continue;
            }
            MatchRule rule = evaluator.parseRule(response.getConditions());
            if (rule == null || rule.isEmpty()) {
                item.setStatus("FALLBACK");
                item.setDetail("无规则，作为兜底候选");
                fallbackItem = item;
                if (fallback == null) {
                    fallback = response;
                }
                chain.add(item);
                continue;
            }
            String reason = evaluator.evaluate(rule, requestLog.getRequestBody(), requestLog.getRequestParams());
            if (reason == null) {
                item.setStatus("HIT");
                item.setDetail("所有条件满足");
                matched = response;
            } else {
                item.setStatus("MISS");
                item.setDetail(reason);
                if (firstMissReason == null) {
                    firstMissReason = reason;
                    firstMissName = response.getName();
                }
            }
            chain.add(item);
        }

        boolean usedFallback = false;
        if (matched == null && fallback != null) {
            matched = fallback;
            usedFallback = true;
            if (fallbackItem != null) {
                fallbackItem.setStatus("FALLBACK_HIT");
                fallbackItem.setDetail("所有条件均未命中，使用兜底返回体");
            }
        } else if (matched != null && fallbackItem != null && matched != fallback) {
            fallbackItem.setStatus("SKIP");
            fallbackItem.setDetail("已命中前一条，兜底未参与");
        }

        AiExplainResult result = new AiExplainResult();
        result.setChain(chain);
        result.setProvider(settings.getProvider());
        result.setModel(settings.getModel());
        if (matched != null) {
            result.setMatchedResponseId(matched.getId());
            result.setMatchedResponseName(matched.getName());
            result.setConclusion((usedFallback ? "使用兜底：" : "命中：") + matched.getName());
            if (single) {
                result.setSummary("当前只有一个启用返回体「" + matched.getName()
                        + "」，请求会直接返回，不参与条件匹配。");
            } else {
                result.setSummary(buildSummary(responses.size(), matched.getName(), usedFallback,
                        firstMissName, firstMissReason));
            }
            result.setSuggestion(buildSuggestion(single, usedFallback, firstMissName, firstMissReason,
                    matched.getName()));
        } else {
            result.setConclusion("未命中任何返回体");
            result.setSummary("启用的返回体都没有命中，且没有可用的兜底返回体。");
            result.setSuggestion("请至少保留一个无规则的兜底返回体，或放宽第一条未命中的条件："
                    + (firstMissReason == null ? "检查字段路径与取值" : firstMissReason));
        }

        if (!AiSettings.PROVIDER_MOCK.equals(settings.getProvider())) {
            enrichWithModel(settings, result);
        }
        return result;
    }

    private String buildSummary(int total, String matchedName, boolean usedFallback,
                                String missName, String missReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(total).append(" 个启用返回体，按顺序匹配。");
        if (usedFallback) {
            sb.append("所有带规则的返回体均未命中，最终返回「").append(matchedName).append("」。");
            if (missReason != null) {
                sb.append("首个未命中项「").append(missName).append("」：").append(missReason).append("。");
            }
        } else {
            sb.append("「").append(matchedName).append("」的条件全部满足，命中后停止匹配。");
            if (missReason != null) {
                sb.append("前序「").append(missName).append("」未通过：").append(missReason).append("。");
            }
        }
        return sb.toString();
    }

    private String buildSuggestion(boolean single, boolean usedFallback,
                                   String missName, String missReason, String matchedName) {
        if (single) {
            return "当前只有一个启用返回体，不会走条件匹配。如需按条件分流，请至少启用两个返回体，并保留一个无规则兜底。";
        }
        if (usedFallback) {
            return "本次走了兜底。若希望「" + missName + "」也覆盖该请求，可放宽其条件："
                    + (missReason == null ? "检查字段路径与取值" : missReason)
                    + "。修改后建议在编辑页用相同请求再验证一次。";
        }
        if (missName != null) {
            return "若希望「" + missName + "」也命中该请求，可放宽条件："
                    + (missReason == null ? "检查字段路径与取值" : missReason) + "。";
        }
        return "当前命中的是第一个满足条件的返回体；新增更具体的规则时，请把它排在兜底返回体之前。";
    }

    /**
     * 让模型把结构化结论改写成更自然的说明；失败时保留模板结果。
     */
    private void enrichWithModel(AiSettings settings, AiExplainResult result) {
        try {
            String system = "你是 MockHub 的匹配诊断助手。只输出 JSON："
                    + "{\"summary\":\"一句话解释为什么命中/未命中\","
                    + "\"suggestion\":\"一句可执行的修正建议，不需要修改时输出空字符串\"}。"
                    + "不要输出 Markdown 或额外文本。";
            StringBuilder user = new StringBuilder();
            user.append("接口已按条件顺序完成匹配，结构化结果如下：\n");
            user.append("- 结论：").append(result.getConclusion()).append("\n");
            for (AiChainItem item : result.getChain()) {
                user.append("- ").append(item.getIndex()).append(". ").append(item.getName())
                        .append(" [").append(item.getStatus()).append("] ").append(item.getDetail()).append("\n");
            }
            user.append("\n请用中文解释并给出修正建议。");
            String raw = provider.complete(settings, system, user.toString());
            String json = raw.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
                String summary = node.path("summary").asText("");
                String suggestion = node.path("suggestion").asText("");
                if (!summary.trim().isEmpty()) {
                    result.setSummary(summary);
                }
                if (!suggestion.trim().isEmpty()) {
                    result.setSuggestion(suggestion);
                }
            }
        } catch (Exception e) {
            log.warn("AI 诊断文案生成失败，使用模板结果：{}", e.getMessage());
        }
    }
}