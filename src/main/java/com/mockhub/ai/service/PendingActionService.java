package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiPendingAction;
import com.mockhub.ai.model.dto.AiApiFullUpdateDraft;
import com.mockhub.ai.model.dto.AiApiUpdateDraft;
import com.mockhub.ai.repository.AiPendingActionRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 待确认操作：创建草稿、确认写入、取消与过期处理。
 * <p>
 * 确认流程先抢占 PENDING → CONFIRMING，再调用现有 ApiService.create，
 * 成功后置为 CONFIRMED；失败回滚为 PENDING，避免重复点击造成重复创建。
 */
@Service
public class PendingActionService {

    private static final Logger log = LoggerFactory.getLogger(PendingActionService.class);

    /** 草稿有效期：12 分钟。 */
    private static final long TTL_MILLIS = 12 * 60 * 1000L;

    private final AiPendingActionRepository repository;
    private final ObjectMapper objectMapper;
    private final ApiService apiService;
    private final PermissionChecker permissionChecker;
    private final LogService logService;

    public PendingActionService(AiPendingActionRepository repository,
                                ObjectMapper objectMapper,
                                ApiService apiService,
                                PermissionChecker permissionChecker,
                                LogService logService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.apiService = apiService;
        this.permissionChecker = permissionChecker;
        this.logService = logService;
    }

    /**
     * 保存一个创建接口草稿，返回给前端渲染确认卡片的字段。
     */
    public Map<String, Object> createApiDraft(ApiDefinitionDTO dto, String summary) {
        AiPendingAction action = new AiPendingAction();
        action.setId(UUID.randomUUID().toString());
        action.setUserId(SecurityContextUtil.getCurrentUserId());
        action.setTeamId(dto.getTeamId());
        action.setActionType("CREATE_API");
        action.setSummary(summary);
        action.setStatus("PENDING");
        action.setCreatedAt(now());
        action.setExpiresAt(format(new Date(System.currentTimeMillis() + TTL_MILLIS)));
        try {
            action.setPayload(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            throw new BizException(50001, "序列化接口草稿失败");
        }
        repository.insert(action);
        log.info("创建 AI 待确认操作：id={}, teamId={}, path={}", action.getId(), dto.getTeamId(), dto.getPath());
        return toCardMap(action, dto);
    }

    /**
     * 保存一个「修改已有接口」草稿。
     */
    public Map<String, Object> createApiUpdateDraft(AiApiUpdateDraft draft, String summary) {
        AiPendingAction action = new AiPendingAction();
        action.setId(UUID.randomUUID().toString());
        action.setUserId(SecurityContextUtil.getCurrentUserId());
        action.setTeamId(draft.getTeamId());
        action.setActionType("UPDATE_API");
        action.setSummary(summary);
        action.setStatus("PENDING");
        action.setCreatedAt(now());
        action.setExpiresAt(format(new Date(System.currentTimeMillis() + TTL_MILLIS)));
        try {
            action.setPayload(objectMapper.writeValueAsString(draft));
        } catch (Exception e) {
            throw new BizException(50001, "序列化接口修改草稿失败");
        }
        repository.insert(action);
        log.info("创建 AI 修改草稿：id={}, apiId={}, responseId={}",
                action.getId(), draft.getApiId(), draft.getResponseId());
        return toUpdateCardMap(action, draft);
    }

    /**
     * 保存一个「整体修改已有接口」草稿（MCP 多返回体）。
     */
    public Map<String, Object> createApiFullUpdateDraft(AiApiFullUpdateDraft draft, String summary) {
        AiPendingAction action = new AiPendingAction();
        action.setId(UUID.randomUUID().toString());
        action.setUserId(SecurityContextUtil.getCurrentUserId());
        action.setTeamId(draft.getTeamId());
        action.setActionType("UPDATE_API_FULL");
        action.setSummary(summary);
        action.setStatus("PENDING");
        action.setCreatedAt(now());
        action.setExpiresAt(format(new Date(System.currentTimeMillis() + TTL_MILLIS)));
        try {
            action.setPayload(objectMapper.writeValueAsString(draft));
        } catch (Exception e) {
            throw new BizException(50001, "序列化接口修改草稿失败");
        }
        repository.insert(action);
        log.info("创建 AI 整体修改草稿：id={}, apiId={}", action.getId(), draft.getApiId());
        return toFullUpdateCardMap(action, draft);
    }

    /**
     * 确认并落库。
     */
    public Map<String, Object> confirm(String id) {
        AiPendingAction action = requireAction(id);
        checkOwner(action);
        if (!"PENDING".equals(action.getStatus())) {
            throw new BizException(40902, "该操作已处理，请刷新确认状态");
        }
        String now = now();
        if (now.compareTo(action.getExpiresAt()) > 0) {
            repository.expire(id, now);
            throw new BizException(40901, "草稿已过期，请让 AI 重新生成");
        }
        int claimed = repository.claim(id, now);
        if (claimed == 0) {
            throw new BizException(40902, "该操作正在处理中或已处理");
        }

        if ("UPDATE_API".equals(action.getActionType())) {
            return confirmUpdate(action, now);
        }
        if ("UPDATE_API_FULL".equals(action.getActionType())) {
            return confirmUpdateFull(action, now);
        }

        ApiDefinitionDTO dto;
        try {
            dto = objectMapper.readValue(action.getPayload(), ApiDefinitionDTO.class);
        } catch (Exception e) {
            repository.release(id);
            throw new BizException(50001, "草稿内容损坏，无法创建");
        }

        try {
            permissionChecker.checkTeamAccess(dto.getTeamId());
            ApiDefinition created = apiService.create(dto);
            repository.complete(id, created.getId(), now());
            recordAudit(action, created, "CONFIRM");
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("apiId", created.getId());
            result.put("name", created.getName());
            result.put("method", created.getMethod());
            result.put("path", created.getPath());
            result.put("status", "CONFIRMED");
            return result;
        } catch (BizException e) {
            repository.release(id);
            throw e;
        } catch (Exception e) {
            repository.release(id);
            log.error("确认 AI 操作失败：id={}", id, e);
            throw new BizException(50001, "创建接口失败，请稍后重试");
        }
    }

    private Map<String, Object> confirmUpdate(AiPendingAction action, String now) {
        AiApiUpdateDraft draft;
        try {
            draft = objectMapper.readValue(action.getPayload(), AiApiUpdateDraft.class);
        } catch (Exception e) {
            repository.release(action.getId());
            throw new BizException(50001, "草稿内容损坏，无法更新");
        }
        try {
            permissionChecker.checkTeamAccess(draft.getTeamId());
            ApiDefinition updated = apiService.updateResponseBody(
                    draft.getApiId(), draft.getResponseId(), draft.getNewBody());
            repository.complete(action.getId(), updated.getId(), now);
            recordAudit(action, updated, "CONFIRM");
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("apiId", updated.getId());
            result.put("name", updated.getName());
            result.put("method", updated.getMethod());
            result.put("path", updated.getPath());
            result.put("status", "CONFIRMED");
            return result;
        } catch (BizException e) {
            repository.release(action.getId());
            throw e;
        } catch (Exception e) {
            repository.release(action.getId());
            log.error("确认 AI 修改失败：id={}", action.getId(), e);
            throw new BizException(50001, "更新接口失败，请稍后重试");
        }
    }

    private Map<String, Object> confirmUpdateFull(AiPendingAction action, String now) {
        AiApiFullUpdateDraft draft;
        try {
            draft = objectMapper.readValue(action.getPayload(), AiApiFullUpdateDraft.class);
        } catch (Exception e) {
            repository.release(action.getId());
            throw new BizException(50001, "草稿内容损坏，无法更新");
        }
        try {
            permissionChecker.checkTeamAccess(draft.getTeamId());
            ApiDefinition updated = apiService.update(draft.getApiId(), draft.getDto());
            repository.complete(action.getId(), updated.getId(), now);
            recordAudit(action, updated, "CONFIRM");
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("apiId", updated.getId());
            result.put("name", updated.getName());
            result.put("method", updated.getMethod());
            result.put("path", updated.getPath());
            result.put("status", "CONFIRMED");
            return result;
        } catch (BizException e) {
            repository.release(action.getId());
            throw e;
        } catch (Exception e) {
            repository.release(action.getId());
            log.error("确认 AI 整体修改失败：id={}", action.getId(), e);
            throw new BizException(50001, "更新接口失败，请稍后重试");
        }
    }

    /**
     * 取消草稿。
     */
    public Map<String, Object> cancel(String id) {
        AiPendingAction action = requireAction(id);
        checkOwner(action);
        if (!"PENDING".equals(action.getStatus())) {
            throw new BizException(40902, "该操作已处理，无法取消");
        }
        String now = now();
        if (now.compareTo(action.getExpiresAt()) > 0) {
            repository.expire(id, now);
            throw new BizException(40901, "草稿已过期，无需取消");
        }
        repository.cancel(id, now);
        recordAudit(action, null, "CANCEL");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("status", "CANCELLED");
        return result;
    }

    public Map<String, Object> get(String id) {
        AiPendingAction action = requireAction(id);
        checkOwner(action);
        if ("PENDING".equals(action.getStatus()) && now().compareTo(action.getExpiresAt()) > 0) {
            repository.expire(id, now());
            action.setStatus("EXPIRED");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", action.getId());
        result.put("status", action.getStatus());
        result.put("actionType", action.getActionType());
        result.put("teamId", action.getTeamId());
        result.put("expiresAt", action.getExpiresAt());
        result.put("resultApiId", action.getResultApiId());
        if ("UPDATE_API_FULL".equals(action.getActionType())) {
            try {
                AiApiFullUpdateDraft draft = objectMapper.readValue(action.getPayload(), AiApiFullUpdateDraft.class);
                Map<String, Object> summary = new LinkedHashMap<String, Object>();
                summary.put("method", draft.getDto().getMethod());
                summary.put("path", draft.getDto().getPath());
                summary.put("name", draft.getDto().getName());
                summary.put("teamId", draft.getTeamId());
                result.put("summary", summary);
                result.put("responseMode", draft.getDto().getResponseMode());
                result.put("responses", responseSummaries(draft.getDto()));
                String preview = draft.getDto().getResponseBody() == null ? "" : draft.getDto().getResponseBody();
                result.put("preview", preview.length() > 8000 ? preview.substring(0, 8000) + "…" : preview);
            } catch (Exception ignored) {
                // 草稿内容损坏时仍返回状态
            }
            return result;
        }
        if ("UPDATE_API".equals(action.getActionType())) {
            try {
                AiApiUpdateDraft draft = objectMapper.readValue(action.getPayload(), AiApiUpdateDraft.class);
                Map<String, Object> summary = new LinkedHashMap<String, Object>();
                summary.put("method", draft.getMethod());
                summary.put("path", draft.getPath());
                summary.put("name", draft.getApiName());
                summary.put("teamId", draft.getTeamId());
                summary.put("responseName", draft.getResponseName());
                result.put("summary", summary);
                String preview = draft.getNewBody() == null ? "" : draft.getNewBody();
                result.put("preview", preview.length() > 8000 ? preview.substring(0, 8000) + "…" : preview);
                List<Map<String, Object>> responses = new ArrayList<Map<String, Object>>();
                Map<String, Object> response = new LinkedHashMap<String, Object>();
                response.put("name", draft.getResponseName());
                response.put("active", true);
                response.put("body", preview);
                response.put("conditions", null);
                responses.add(response);
                result.put("responses", responses);
                String oldPreview = draft.getOldBody() == null ? "" : draft.getOldBody();
                result.put("oldPreview", oldPreview.length() > 2000 ? oldPreview.substring(0, 2000) + "…" : oldPreview);
            } catch (Exception ignored) {
                // 草稿内容损坏时仍返回状态
            }
            return result;
        }
        try {
            ApiDefinitionDTO dto = objectMapper.readValue(action.getPayload(), ApiDefinitionDTO.class);
            Map<String, Object> summary = new LinkedHashMap<String, Object>();
            summary.put("method", dto.getMethod());
            summary.put("path", dto.getPath());
            summary.put("name", dto.getName());
            summary.put("teamId", dto.getTeamId());
            result.put("summary", summary);
            result.put("responseMode", dto.getResponseMode());
            result.put("responses", responseSummaries(dto));
            String preview = dto.getResponseBody() == null ? "" : dto.getResponseBody();
            result.put("preview", preview.length() > 8000 ? preview.substring(0, 8000) + "…" : preview);
        } catch (Exception ignored) {
            // 草稿内容损坏时仍返回状态，确认时会给出明确错误
        }
        return result;
    }

    private List<Map<String, Object>> responseSummaries(ApiDefinitionDTO dto) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (dto.getResponses() == null) {
            return result;
        }
        for (ApiResponseDTO response : dto.getResponses()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", response.getName());
            item.put("responseCode", response.getResponseCode());
            item.put("active", response.isActive());
            item.put("conditions", response.getConditions());
            item.put("bodyType", response.getBodyType());
            String body = response.getResponseBody() == null ? "" : response.getResponseBody();
            item.put("body", body.length() > 2000 ? body.substring(0, 2000) + "…" : body);
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> toCardMap(AiPendingAction action, ApiDefinitionDTO dto) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("method", dto.getMethod());
        summary.put("path", dto.getPath());
        summary.put("name", dto.getName());
        summary.put("teamId", dto.getTeamId());

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "PENDING_ACTION");
        card.put("actionId", action.getId());
        card.put("actionType", action.getActionType());
        card.put("status", action.getStatus());
        card.put("expiresAt", action.getExpiresAt());
        card.put("summary", summary);
        String preview = dto.getResponseBody() == null ? "" : dto.getResponseBody();
        card.put("preview", preview.length() > 8000 ? preview.substring(0, 8000) + "…" : preview);
        return card;
    }

    private Map<String, Object> toUpdateCardMap(AiPendingAction action, AiApiUpdateDraft draft) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("method", draft.getMethod());
        summary.put("path", draft.getPath());
        summary.put("name", draft.getApiName());
        summary.put("teamId", draft.getTeamId());
        summary.put("responseName", draft.getResponseName());

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "PENDING_ACTION");
        card.put("actionId", action.getId());
        card.put("actionType", action.getActionType());
        card.put("status", action.getStatus());
        card.put("expiresAt", action.getExpiresAt());
        card.put("summary", summary);
        String preview = draft.getNewBody() == null ? "" : draft.getNewBody();
        card.put("preview", preview.length() > 8000 ? preview.substring(0, 8000) + "…" : preview);
        String oldPreview = draft.getOldBody() == null ? "" : draft.getOldBody();
        card.put("oldPreview", oldPreview.length() > 8000 ? oldPreview.substring(0, 8000) + "…" : oldPreview);
        return card;
    }

    private Map<String, Object> toFullUpdateCardMap(AiPendingAction action, AiApiFullUpdateDraft draft) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("method", draft.getDto().getMethod());
        summary.put("path", draft.getDto().getPath());
        summary.put("name", draft.getDto().getName());
        summary.put("teamId", draft.getTeamId());

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "PENDING_ACTION");
        card.put("actionId", action.getId());
        card.put("actionType", action.getActionType());
        card.put("status", action.getStatus());
        card.put("expiresAt", action.getExpiresAt());
        card.put("summary", summary);
        String preview = draft.getDto().getResponseBody() == null ? "" : draft.getDto().getResponseBody();
        card.put("preview", preview.length() > 8000 ? preview.substring(0, 8000) + "…" : preview);
        return card;
    }

    private AiPendingAction requireAction(String id) {
        AiPendingAction action = repository.findById(id);
        if (action == null) {
            throw new BizException(40404, "待确认操作不存在或已被清理");
        }
        return action;
    }

    private void checkOwner(AiPendingAction action) {
        if (!SecurityContextUtil.isSuperAdmin()
                && !SecurityContextUtil.getCurrentUserId().equals(action.getUserId())) {
            throw new BizException(40305, "无权操作他人的 AI 草稿");
        }
    }

    private void recordAudit(AiPendingAction action, ApiDefinition created, String actionName) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setId(UUID.randomUUID().toString());
            opLog.setTeamId(action.getTeamId());
            opLog.setUserId(SecurityContextUtil.getCurrentUserId());
            opLog.setUsername(SecurityContextUtil.getCurrentUsername());
            opLog.setAction(actionName);
            opLog.setTargetType("AI_ACTION");
            opLog.setTargetId(action.getId());
            opLog.setTargetName(created == null ? "AI 草稿" : created.getName());
            if (created == null) {
                opLog.setDetail("取消 AI 草稿");
            } else if ("UPDATE_API".equals(action.getActionType())
                    || "UPDATE_API_FULL".equals(action.getActionType())) {
                opLog.setDetail("确认 AI 修改接口：" + created.getMethod() + " " + created.getPath());
            } else {
                opLog.setDetail("确认 AI 创建的接口：" + created.getMethod() + " " + created.getPath());
            }
            opLog.setCreatedAt(now());
            logService.logOperation(opLog);
        } catch (Exception e) {
            log.warn("记录 AI 操作审计日志失败：{}", e.getMessage());
        }
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
    }

    private String format(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(date);
    }
}