package com.mockhub.system.model.entity;

/**
 * 用户-团队关联实体
 * <p>
 * 对应数据库 user_team 表，维护用户与团队的多对多关系及团队内角色。
 */
public class UserTeam {

    /** 用户 ID */
    private String userId;

    /** 团队 ID */
    private String teamId;

    /** 团队内角色：TEAM_ADMIN / MEMBER */
    private String role;

    public UserTeam() {
    }

    public UserTeam(String userId, String teamId, String role) {
        this.userId = userId;
        this.teamId = teamId;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "UserTeam{" +
                "userId='" + userId + '\'' +
                ", teamId='" + teamId + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
