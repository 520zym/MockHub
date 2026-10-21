package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.model.AiConversationMessage;
import com.mockhub.ai.repository.AiConversationRepository;
import com.mockhub.common.model.BizException;
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

    public AiConversationService(AiConversationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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
        try {
            assistantMessage.setCards(cards == null || cards.isEmpty()
                    ? "[]" : objectMapper.writeValueAsString(cards));
        } catch (Exception e) {
            assistantMessage.setCards("[]");
        }
        repository.insertMessage(assistantMessage);

        if (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty()) {
            conversation.setTitle(title(userText));
        }
        conversation.setUpdatedAt(assistantMessage.getCreatedAt());
        repository.updateMeta(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt());
        return conversation;
    }

    public List<AiConversation> list(String userId) {
        List<AiConversation> list = repository.listByUser(userId, 30);
        for (AiConversation conversation : list) {
            conversation.setPreview(preview(conversation.getPreview()));
            if (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty()) {
                conversation.setTitle("未命名会话");
            }
        }
        return list;
    }

    public Map<String, Object> detail(String id, String userId) {
        AiConversation conversation = repository.find(id, userId);
        if (conversation == null) {
            throw new BizException(40404, "会话不存在或已被删除");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", conversation.getId());
        result.put("title", conversation.getTitle());
        result.put("teamId", conversation.getTeamId());
        result.put("createdAt", conversation.getCreatedAt());
        result.put("updatedAt", conversation.getUpdatedAt());
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        for (AiConversationMessage message : repository.listMessages(id)) {
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

    public void delete(String id, String userId) {
        AiConversation conversation = repository.find(id, userId);
        if (conversation == null) {
            throw new BizException(40404, "会话不存在或已被删除");
        }
        repository.delete(id, userId);
    }

    private JsonNode parseCards(String cards) {
        if (cards == null || cards.trim().isEmpty()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(cards);
            return node.isArray() ? node : objectMapper.createArrayNode();
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
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