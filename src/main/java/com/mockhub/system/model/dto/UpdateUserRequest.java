package com.mockhub.system.model.dto;

/**
 * 修改用户请求体（不含密码和团队分配）
 */
public class UpdateUserRequest {

    /** 显示名称 */
    private String displayName;

    /** 全局角色：SUPER_ADMIN / USER */
    private String globalRole;

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
