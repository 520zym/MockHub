package com.mockhub.ai.model.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话响应：文本 + 可交互卡片（统计 / 搜索 / 待确认操作）。
 */
public class AiChatResponse {

    private String reply;
    private List<Map<String, Object>> cards = new ArrayList<Map<String, Object>>();
    private String provider;
    private String model;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<Map<String, Object>> getCards() {
        return cards;
    }

    public void setCards(List<Map<String, Object>> cards) {
        this.cards = cards;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
