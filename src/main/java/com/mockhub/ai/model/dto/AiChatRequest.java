package com.mockhub.ai.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话请求：前端携带最近几轮历史，服务端不保存会话。
 */
public class AiChatRequest {

    private String teamId;
    /** 当前正在编辑或刚创建的接口 ID，供「修改已有接口」使用。 */
    private String contextApiId;
    private String conversationId;
    private String message;
    private List<AiChatMessage> history = new ArrayList<AiChatMessage>();

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getContextApiId() {
        return contextApiId;
    }

    public void setContextApiId(String contextApiId) {
        this.contextApiId = contextApiId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<AiChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<AiChatMessage> history) {
        this.history = history;
    }
}