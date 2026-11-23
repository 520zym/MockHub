package com.mockhub.auth.model;

/** Safe session view. Does not expose authentication versions or credentials. */
public class WebSessionInfo {
    private final String id;
    private final String createdAt;
    private final long expiresAt;
    private final String userAgent;
    private final String ipAddress;
    private final boolean current;

    public WebSessionInfo(AuthSession session, String currentSessionId) {
        id = session.getId(); createdAt = session.getCreatedAt(); expiresAt = session.getExpiresAt();
        userAgent = session.getUserAgent(); ipAddress = session.getIpAddress();
        current = id.equals(currentSessionId);
    }
    public String getId() { return id; }
    public String getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public boolean isCurrent() { return current; }
}
