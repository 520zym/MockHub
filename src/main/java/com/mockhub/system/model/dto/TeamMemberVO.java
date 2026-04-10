package com.mockhub.system.model.dto;

/**
 * 团队成员视图对象
 */
public class TeamMemberVO {

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 团队内角色：TEAM_ADMIN / MEMBER */
    private String role;

    public TeamMemberVO() {
    }

    public TeamMemberVO(String userId, String username, String displayName, String role) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
