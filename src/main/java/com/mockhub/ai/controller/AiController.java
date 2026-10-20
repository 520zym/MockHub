package com.mockhub.ai.controller;

import com.mockhub.ai.model.dto.AiConditionsResult;
import com.mockhub.ai.model.dto.AiChatRequest;
import com.mockhub.ai.model.dto.AiChatResponse;
import com.mockhub.ai.model.dto.AiExplainRequest;
import com.mockhub.ai.model.dto.AiExplainResult;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.ai.model.dto.AiSettingsDTO;
import com.mockhub.ai.model.dto.AiSettingsUpdateRequest;
import com.mockhub.ai.model.dto.AiStatusDTO;
import com.mockhub.ai.service.AiChatService;
import com.mockhub.ai.service.AiExplainService;
import com.mockhub.ai.service.AiService;
import com.mockhub.ai.service.AiSettingsService;
import com.mockhub.ai.service.PendingActionService;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.Result;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.service.LogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * AI 助手配置与状态接口。
 * <p>
 * 状态接口对所有登录用户开放；配置读写与连通性测试仅超级管理员可用。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiSettingsService settingsService;
    private final AiService aiService;
    private final AiExplainService explainService;
    private final AiChatService chatService;
    private final PendingActionService pendingActionService;
    private final LogService logService;

    public AiController(AiSettingsService settingsService, AiService aiService,
                        AiExplainService explainService, AiChatService chatService,
                        PendingActionService pendingActionService, LogService logService) {
        this.settingsService = settingsService;
        this.aiService = aiService;
        this.explainService = explainService;
        this.chatService = chatService;
        this.pendingActionService = pendingActionService;
        this.logService = logService;
    }

    /** 当前用户可见的 AI 状态（入口是否渲染、能力是否可用）。 */
    @GetMapping("/status")
    public Result<AiStatusDTO> status() {
        return Result.ok(settingsService.status());
    }

    /** 读取 AI 配置；仅超管。 */
    @GetMapping("/settings")
    public Result<AiSettingsDTO> getSettings() {
        checkSuperAdmin();
        return Result.ok(settingsService.getDto());
    }

    /** 保存 AI 配置；仅超管。 */
    @PutMapping("/settings")
    public Result<AiSettingsDTO> saveSettings(@RequestBody AiSettingsUpdateRequest request) {
        checkSuperAdmin();
        AiSettingsDTO saved = settingsService.update(request);
        record("UPDATE", "更新 AI 助手配置：enabled=" + saved.isEnabled()
                + ", provider=" + saved.getProvider() + ", model=" + saved.getModel());
        return Result.ok(saved);
    }

    /** 生成响应体（REST JSON / SOAP XML），由返回体编辑器调用。 */
    @PostMapping("/generate-response")
    public Result<AiResponseResult> generateResponse(@RequestBody AiGenerateRequest request) {
        return Result.ok(aiService.generateResponse(request));
    }

    /** 把自然语言描述转成现有 conditions 结构。 */
    @PostMapping("/generate-conditions")
    public Result<AiConditionsResult> generateConditions(@RequestBody AiGenerateRequest request) {
        return Result.ok(aiService.generateConditions(request));
    }

    /** 解释一次请求为什么命中 / 未命中。 */
    @PostMapping("/explain-match")
    public Result<AiExplainResult> explainMatch(@RequestBody AiExplainRequest request) {
        return Result.ok(explainService.explain(request.getRequestLogId()));
    }

    /** 对话助手：意图识别 + 只读查询 / 生成待确认草稿。 */
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        return Result.ok(chatService.chat(request));
    }

    /** 查询待确认操作状态。 */
    @GetMapping("/actions/{id}")
    public Result<Map<String, Object>> getAction(@PathVariable String id) {
        return Result.ok(pendingActionService.get(id));
    }

    /** 确认并执行待确认操作。 */
    @PostMapping("/actions/{id}/confirm")
    public Result<Map<String, Object>> confirmAction(@PathVariable String id) {
        return Result.ok(pendingActionService.confirm(id));
    }

    /** 取消待确认操作。 */
    @PostMapping("/actions/{id}/cancel")
    public Result<Map<String, Object>> cancelAction(@PathVariable String id) {
        return Result.ok(pendingActionService.cancel(id));
    }

    /** 测试模型服务连通性；仅超管。 */
    @PostMapping("/settings/test")
    public Result<Map<String, Object>> testConnection() {
        checkSuperAdmin();
        return Result.ok(settingsService.testConnection());
    }

    private void checkSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40101, "无操作权限");
        }
    }

    private void record(String action, String detail) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setId(UUID.randomUUID().toString());
            opLog.setTeamId(null);
            opLog.setUserId(SecurityContextUtil.getCurrentUserId());
            opLog.setUsername(SecurityContextUtil.getCurrentUsername());
            opLog.setAction(action);
            opLog.setTargetType("AI_SETTINGS");
            opLog.setTargetId("ai");
            opLog.setTargetName("AI 助手");
            opLog.setDetail(detail);
            opLog.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            logService.logOperation(opLog);
        } catch (Exception ignored) {
            // 审计日志失败不应阻断配置保存
        }
    }
}