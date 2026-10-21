package com.mockhub.ai.model.dto;

/**
 * AI 配置保存请求。
 * <p>
 * apiKey 语义：null = 保持原值；空字符串 = 清除；其他 = 替换为新 Key。
 */
public class AiSettingsUpdateRequest {

    private boolean enabled;
    private String provider;
    private String vendor;
    private String baseUrl;
    private String model;
    private String apiKey;

    private boolean capResponseBody;
    private boolean capConditions;
    private boolean capExplain;
    private boolean capDrafts;

    private String accessScope;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isCapResponseBody() {
        return capResponseBody;
    }

    public void setCapResponseBody(boolean capResponseBody) {
        this.capResponseBody = capResponseBody;
    }

    public boolean isCapConditions() {
        return capConditions;
    }

    public void setCapConditions(boolean capConditions) {
        this.capConditions = capConditions;
    }

    public boolean isCapExplain() {
        return capExplain;
    }

    public void setCapExplain(boolean capExplain) {
        this.capExplain = capExplain;
    }

    public boolean isCapDrafts() {
        return capDrafts;
    }

    public void setCapDrafts(boolean capDrafts) {
        this.capDrafts = capDrafts;
    }

    public String getAccessScope() {
        return accessScope;
    }

    public void setAccessScope(String accessScope) {
        this.accessScope = accessScope;
    }
}