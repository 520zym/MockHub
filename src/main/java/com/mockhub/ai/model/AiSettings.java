package com.mockhub.ai.model;

/**
 * AI 助手全局配置（单行，id 固定为 default）。
 * <p>
 * API Key 只用于服务端调用模型，接口层只返回是否已配置与掩码，不返回明文。
 */
public class AiSettings {

    public static final String DEFAULT_ID = "default";
    public static final String PROVIDER_OPENAI = "OPENAI_COMPATIBLE";
    public static final String PROVIDER_MOCK = "MOCK";
    public static final String SCOPE_ALL = "ALL_MEMBERS";
    public static final String SCOPE_ADMIN = "SUPER_ADMIN_ONLY";

    private String id = DEFAULT_ID;
    private boolean enabled;
    private String provider = PROVIDER_OPENAI;
    private String vendor = "custom";
    private String baseUrl;
    private String model;
    private String apiKey;

    private boolean capResponseBody = true;
    private boolean capConditions = true;
    private boolean capExplain = true;
    private boolean capDrafts = true;

    private String accessScope = SCOPE_ALL;
    private String updatedAt;
    private String updatedBy;

    /** 仅运行时：Key 解密失败，不落库 */
    private boolean keyInvalid;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public boolean isKeyInvalid() {
        return keyInvalid;
    }

    public void setKeyInvalid(boolean keyInvalid) {
        this.keyInvalid = keyInvalid;
    }
}