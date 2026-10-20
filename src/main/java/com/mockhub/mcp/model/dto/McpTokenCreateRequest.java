package com.mockhub.mcp.model.dto;

/**
 * 创建 Token 请求。expiresInDays=0 表示永不过期。
 */
public class McpTokenCreateRequest {

    private String name;
    private int expiresInDays = 90;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getExpiresInDays() { return expiresInDays; }
    public void setExpiresInDays(int expiresInDays) { this.expiresInDays = expiresInDays; }
}
