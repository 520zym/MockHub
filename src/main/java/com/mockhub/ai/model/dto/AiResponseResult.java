package com.mockhub.ai.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 生成响应体结果。
 */
public class AiResponseResult {

    private String content;
    private String contentType;
    private List<String> warnings = new ArrayList<String>();
    private String provider;
    private String model;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
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
