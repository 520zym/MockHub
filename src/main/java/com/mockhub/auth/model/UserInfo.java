package com.mockhub.auth.model;

import java.util.List;

/**
 * 登录成功后返回的用户信息
 * <p>
 * 嵌套在 {@link LoginResponse} 中。包含用户基本信息和所属团队列表，
 * 前端据此初始化用户状态（角色判断、团队筛选等）。
 */
public class UserInfo {

    /** 用户 ID */
    private String id;

    /** 用户名 */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 全局角色：SUPER_ADMIN / USER */
    private String globalRole;

    /** 是否首次登录（true 时前端强制跳转修改密码页） */
    private boolean firstLogin;

    /** 用户所属团队及角色列表 */
    private List<TeamRole> teams;

    public UserInfo() {
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

    public List<TeamRole> getTeams() {
        return teams;
    }

    public void setTeams(List<TeamRole> teams) {
        this.teams = teams;
    }
}
