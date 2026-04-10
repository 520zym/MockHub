package com.mockhub.auth.model;

/**
 * 用户所属团队及角色信息
 * <p>
 * 嵌套在 {@link UserInfo} 中，展示用户在各团队中的角色。
 * 前端据此判断用户对团队数据的操作权限。
 */
public class TeamRole {

    /** 团队 ID */
    private String teamId;

    /** 团队名称 */
    private String teamName;

    /** 团队短标识，如 "FE"、"BE" */
    private String identifier;

    /** 用户在该团队中的角色：TEAM_ADMIN / MEMBER */
    private String role;

    public TeamRole() {
    }

    public TeamRole(String teamId, String teamName, String identifier, String role) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.identifier = identifier;
        this.role = role;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
