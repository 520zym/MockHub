package com.mockhub.ai.model.dto;

/**
 * AI 配置读取结果。
 * <p>
 * 不包含 apiKey 明文，只返回 keyConfigured 与 keyMask；updatedAt 供设置页展示。
 */
public class AiSettingsDTO {

    private boolean enabled;
    private String provider;
    private String vendor;
    private String baseUrl;
    private String model;
    private boolean keyConfigured;
    private String keyMask;

    private boolean capResponseBody;
    private boolean capConditions;
    private boolean capExplain;
    private boolean capDrafts;

    private String accessScope;
    private String updatedAt;

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

    public boolean isKeyConfigured() {
        return keyConfigured;
    }

    public void setKeyConfigured(boolean keyConfigured) {
        this.keyConfigured = keyConfigured;
    }

    public String getKeyMask() {
        return keyMask;
    }

    public void setKeyMask(String keyMask) {
        this.keyMask = keyMask;
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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}