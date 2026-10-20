package com.mockhub.mcp.model;

/**
 * MCP 访问 Token。仅保存哈希与前缀，明文只在创建时返回一次。
 */
public class McpToken {

    private String id;
    private String name;
    private String tokenHash;
    private String tokenPrefix;
    private String userId;
    private String createdAt;
    private String lastUsedAt;
    private String expiresAt;
    private String revokedAt;
    private String ownerName;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(String lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public String getRevokedAt() { return revokedAt; }
    public void setRevokedAt(String revokedAt) { this.revokedAt = revokedAt; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
}
