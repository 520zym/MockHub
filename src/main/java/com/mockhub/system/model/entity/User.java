package com.mockhub.system.model.entity;

import java.util.Map;

/**
 * 用户实体
 * <p>
 * 对应数据库 user 表，包含基本信息和全局角色。
 * 团队角色关系通过 user_team 关联表维护。
 * <p>
 * teamRoles 为非持久化字段（Map&lt;teamId, role&gt;），
 * 由 Service 层查询 user_team 表后手动填充，供认证等场景使用。
 */
public class User {

    /** 用户 ID（UUID） */
    private String id;

    /** 用户名，全局唯一 */
    private String username;

    /** BCrypt 加密后的密码哈希 */
    private String passwordHash;

    /** 显示名称 */
    private String displayName;

    /** 全局角色：SUPER_ADMIN / TEAM_ADMIN / MEMBER */
    private String globalRole;

    /** 是否首次登录（首次登录需强制修改密码） */
    private boolean firstLogin;

    /** 创建时间（ISO 格式） */
    private String createdAt;

    /** 更新时间（ISO 格式） */
    private String updatedAt;

    /**
     * 非持久化字段：用户在各团队中的角色映射（teamId → role）。
     * 由 Service 层按需填充，不直接映射数据库列。
     */
    private Map<String, String> teamRoles;

    public User() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGlobalRole() {
        return globalRole;
    }

    public void setGlobalRole(String globalRole) {
        this.globalRole = globalRole;
    }

    public boolean isFirstLogin() {
        return firstLogin;
    }

    public void setFirstLogin(boolean firstLogin) {
        this.firstLogin = firstLogin;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, String> getTeamRoles() {
        return teamRoles;
    }

    public void setTeamRoles(Map<String, String> teamRoles) {
        this.teamRoles = teamRoles;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", globalRole='" + globalRole + '\'' +
                ", firstLogin=" + firstLogin +
                '}';
    }
}
