package com.mockhub.system.model.dto;

/** Exact-match account discovery intentionally exposes no platform roles, teams or credentials. */
public class MemberCandidateVO {
    private final String userId;
    private final String username;
    private final String displayName;
    public MemberCandidateVO(String userId, String username, String displayName) {
        this.userId=userId; this.username=username; this.displayName=displayName;
    }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
}
