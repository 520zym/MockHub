package com.mockhub.ai.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话请求：前端携带最近几轮历史，服务端不保存会话。
 */
public class AiChatRequest {

    private String teamId;
    private String message;
    private List<AiChatMessage> history = new ArrayList<AiChatMessage>();

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
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
