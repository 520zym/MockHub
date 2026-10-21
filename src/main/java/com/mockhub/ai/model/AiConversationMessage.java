package com.mockhub.ai.model;

/** AI 对话中的一条消息；cards 保存为 JSON 字符串。 */
public class AiConversationMessage {

    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String cards;
    private String provider;
    private String model;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCards() { return cards; }
    public void setCards(String cards) { this.cards = cards; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
