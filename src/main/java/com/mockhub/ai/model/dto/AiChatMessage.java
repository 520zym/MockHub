package com.mockhub.ai.model.dto;

/**
 * 对话历史中的一条消息，role 取值 user / assistant。
 */
public class AiChatMessage {

    private String role;
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
