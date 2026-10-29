package com.mockhub.auth.model;

/** Persisted web login session. expiresAt is epoch milliseconds; no bearer credentials are stored. */
public class AuthSession {
    private String id;
    private String userId;
    private long authVersion;
    private String createdAt;
    private long expiresAt;
    private String revokedAt;
    private String userAgent;
    private String ipAddress;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getAuthVersion() { return authVersion; }
    public void setAuthVersion(long authVersion) { this.authVersion = authVersion; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public String getRevokedAt() { return revokedAt; }
    public void setRevokedAt(String revokedAt) { this.revokedAt = revokedAt; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
