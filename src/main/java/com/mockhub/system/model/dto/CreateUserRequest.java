package com.mockhub.system.model.dto;

/**
 * 创建用户请求体
 */
public class CreateUserRequest {

    /** 用户名 */
    private String username;

    /** 初始密码 */
    private String password;

    /** 显示名称 */
    private String displayName;

    /** 全局角色：SUPER_ADMIN / USER */
    private String globalRole;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
}
