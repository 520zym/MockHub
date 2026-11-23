package com.mockhub.ai.model.dto;

/**
 * 保存前的连接测试请求。
 * <p>
 * apiKey 语义：null = 使用已保存的 Key；空字符串 = 明确不使用 Key（例如 Ollama）；
 * 其他 = 使用本次输入的新 Key。测试不会写入数据库。
 */
public class AiConnectionTestRequest {
    private String provider;
    private String vendor;
    private String baseUrl;
    private String apiKey;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
