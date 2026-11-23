package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.model.AiConversationMessage;
import com.mockhub.ai.repository.AiConversationRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** AI 对话历史：每个用户只能查看/继续自己的会话。 */
@Service
public class AiConversationService {

    private static final int MAX_TITLE = 30;
    private static final int MAX_PREVIEW = 80;

    private final AiConversationRepository repository;
    private final ObjectMapper objectMapper;
    private final PermissionChecker permissions;
    private final TeamRepository teams;

    public AiConversationService(AiConversationRepository repository, ObjectMapper objectMapper,
                                 PermissionChecker permissions, TeamRepository teams) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.permissions = permissions;
        this.teams = teams;
    }

    /** Validate before provider calls, resource queries or draft creation, not just before persistence. */
    public void validateContext(String conversationId, String userId, String teamId) {
        requireCurrentOwner(userId);
        requireReadableTeam(teamId);
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            AiConversation conversation = requireReadableConversation(conversationId, userId);
            if (!teamId.equals(conversation.getTeamId())) {
                throw new BizException(40102, "会话不属于当前团队，请新建会话");
            }
            requireConsistentHistory(conversation, repository.listMessages(conversationId));
        }
    }

    private AiConversation requireReadableConversation(String id, String userId) {
        requireCurrentOwner(userId);
        AiConversation conversation = repository.find(id, userId);
        if (conversation == null) throw new BizException(40404, "会话不存在或已被删除");
        requireReadableTeam(conversation.getTeamId());
        return conversation;
    }

    private void requireCurrentOwner(String userId) {
        if (userId == null || !SecurityContextUtil.getCurrentUserId().equals(userId)) {
            throw new BizException(40305, "无权读取他人的 AI 会话");
        }
    }

    private void requireReadableTeam(String teamId) {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new BizException(40102, "历史会话未绑定团队，不能读取或继续");
        }
        permissions.checkTeamAccess(teamId);
        if (teams.findById(teamId) == null) throw new BizException(40403, "团队不存在");
    }

    /**
     * 追加一轮用户消息 + 助手回复；conversationId 为空时新建会话。
     */
    public AiConversation appendExchange(String conversationId, String userId, String teamId,
                                         String userText, String reply,
                                         List<Map<String, Object>> cards,
                                         String provider, String model) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BizException(40002, "未登录");
        }
        validateContext(conversationId, userId, teamId);
        JsonNode storedCards = cards == null ? objectMapper.createArrayNode() : objectMapper.valueToTree(cards);
        requireCardScopes(teamId, storedCards);
        AiConversation conversation;
        if (conversationId == null || conversationId.trim().isEmpty()) {
            conversation = new AiConversation();
            conversation.setId(UUID.randomUUID().toString());
            conversation.setUserId(userId);
            conversation.setTeamId(teamId);
            conversation.setTitle(title(userText));
            conversation.setCreatedAt(now());
            conversation.setUpdatedAt(conversation.getCreatedAt());
            repository.insert(conversation);
        } else {
            conversation = repository.find(conversationId, userId);
            if (conversation == null) {
                throw new BizException(40404, "会话不存在或已被删除");
            }
            if (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty()) {
                conversation.setTitle(title(userText));
            }
        }

        String timestamp = now();
        AiConversationMessage userMessage = new AiConversationMessage();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole("user");
        userMessage.setContent(userText);
        userMessage.setCreatedAt(timestamp);
        repository.insertMessage(userMessage);

        AiConversationMessage assistantMessage = new AiConversationMessage();
        assistantMessage.setId(UUID.randomUUID().toString());
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(reply);
        assistantMessage.setProvider(provider);
        assistantMessage.setModel(model);
        assistantMessage.setCreatedAt(now());
        assistantMessage.setCards(storedCards.toString());
        repository.insertMessage(assistantMessage);

        if (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty()) {
            conversation.setTitle(title(userText));
        }
        conversation.setUpdatedAt(assistantMessage.getCreatedAt());
        repository.updateMeta(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt());
        return conversation;
    }

    public List<AiConversation> list(String userId) {
        requireCurrentOwner(userId);
        List<AiConversation> list = new ArrayList<AiConversation>();
        // Legacy callers omit teamId: return only their currently readable, team-bound history.
        for (AiConversation conversation : repository.listByUser(userId, 30)) {
            try {
                requireReadableTeam(conversation.getTeamId());
                requireConsistentHistory(conversation, repository.listMessages(conversation.getId()));
            } catch (BizException denied) {
                if (denied.getCode() == 40102 || denied.getCode() == 40403) continue;
                throw denied;
            }
            list.add(conversation);
            conversation.setPreview(preview(conversation.getPreview()));
            if (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty()) {
                conversation.setTitle("未命名会话");
            }
        }
        return list;
    }

    public Map<String, Object> detail(String id, String userId) {
        AiConversation conversation = requireReadableConversation(id, userId);
        List<AiConversationMessage> storedMessages = repository.listMessages(id);
        requireConsistentHistory(conversation, storedMessages);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", conversation.getId());
        result.put("title", conversation.getTitle());
        result.put("teamId", conversation.getTeamId());
        result.put("createdAt", conversation.getCreatedAt());
        result.put("updatedAt", conversation.getUpdatedAt());
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        for (AiConversationMessage message : storedMessages) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", message.getId());
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            item.put("provider", message.getProvider());
            item.put("model", message.getModel());
            item.put("createdAt", message.getCreatedAt());
            item.put("cards", parseCards(message.getCards()));
            messages.add(item);
        }
        result.put("messages", messages);
        return result;
    }

    /** 只更新标题，不改变会话排序时间；仅会话所有者可操作。 */
    public void rename(String id, String userId, String title) {
        requireCurrentOwner(userId);
        AiConversation conversation = repository.find(id, userId);
        if (conversation == null) throw new BizException(40404, "会话不存在或已被删除");
        String value = normalizeTitle(title);
        if (value == null) return;
        conversation.setTitle(value);
        repository.updateTitle(id, value);
    }

    public void delete(String id, String userId) {
        requireCurrentOwner(userId); // Owner cleanup remains possible after leaving the team; no read/write grant required.
        AiConversation conversation = repository.find(id, userId);
        if (conversation == null) {
            throw new BizException(40404, "会话不存在或已被删除");
        }
        repository.delete(id, userId);
    }

    /** Quarantine mixed histories on read; keep the original database rows for owner cleanup/recovery. */
    private void requireConsistentHistory(AiConversation conversation, List<AiConversationMessage> messages) {
        for (AiConversationMessage message : messages) {
            requireCardScopes(conversation.getTeamId(), parseCards(message.getCards()));
        }
    }

    private void requireCardScopes(String teamId, JsonNode cards) {
        if (!cards.isArray()) throw mixedHistory();
        for (JsonNode card : cards) requireCardScope(teamId, card, false);
    }

    // Only inspect server card metadata. A response example may legitimately contain business
    // fields named teamId/apiId; never interpret preview/body JSON as authorization metadata.
    private boolean requireCardScope(String teamId, JsonNode node, boolean inheritedScope) {
        if (!node.isObject()) throw mixedHistory();
        String declaredTeam = metadataText(node, "teamId");
        if (declaredTeam != null && !teamId.equals(declaredTeam)) throw mixedHistory();
        boolean scoped = inheritedScope || declaredTeam != null;
        JsonNode summary = node.get("summary");
        if (summary != null && !summary.isNull()) scoped |= requireCardScope(teamId, summary, scoped);
        String apiId = metadataText(node, "apiId");
        if (apiId != null) {
            String actualTeam = repository.findApiTeam(apiId);
            if (actualTeam != null && !teamId.equals(actualTeam)) throw mixedHistory();
            scoped |= actualTeam != null;
            if (!scoped) throw mixedHistory();
        }
        String actionId = metadataText(node, "actionId");
        if (actionId != null) {
            String actualTeam = repository.findActionTeam(actionId);
            if (actualTeam != null && !teamId.equals(actualTeam)) throw mixedHistory();
            scoped |= actualTeam != null;
            if (!scoped) throw mixedHistory();
        }
        JsonNode items = node.get("items");
        if (items != null && !items.isNull()) {
            if (!items.isArray()) throw mixedHistory();
            for (JsonNode item : items) requireCardScope(teamId, item, scoped);
        }
        return scoped;
    }

    private String metadataText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw mixedHistory();
        return value.asText().trim().isEmpty() ? null : value.asText();
    }

    private BizException mixedHistory() {
        return new BizException(40102, "历史会话含跨团队或无法确认团队的卡片，已隔离；可删除该会话后重新开始");
    }

    private JsonNode parseCards(String cards) {
        if (cards == null || cards.trim().isEmpty()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(cards);
            if (node == null || !node.isArray()) throw mixedHistory();
            return node;
        } catch (Exception e) {
            throw mixedHistory();
        }
    }

    private String normalizeTitle(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        value = value.replaceAll("^[“”\"'《》]+|[“”\"'《》]+$", "").replaceAll("[。！？!?；;：:，,]+$", "").trim();
        if (value.isEmpty()) return null;
        return value.length() <= MAX_TITLE ? value : value.substring(0, MAX_TITLE) + "…";
    }

    private String title(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return "新会话";
        }
        return value.length() <= MAX_TITLE ? value : value.substring(0, MAX_TITLE) + "…";
    }

    private String preview(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return "";
        }
        return value.length() <= MAX_PREVIEW ? value : value.substring(0, MAX_PREVIEW) + "…";
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(new Date());
    }
}