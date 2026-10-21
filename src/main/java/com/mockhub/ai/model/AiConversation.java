package com.mockhub.ai.model;

/** AI 对话会话（列表页使用，messageCount/preview 为查询派生字段）。 */
public class AiConversation {

    private String id;
    private String userId;
    private String teamId;
    private String title;
    private String createdAt;
    private String updatedAt;
    private int messageCount;
    private String preview;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }
}
