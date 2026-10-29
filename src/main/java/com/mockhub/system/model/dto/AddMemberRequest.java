package com.mockhub.system.model.dto;

/**
 * 添加团队成员请求体
 */
public class AddMemberRequest {

    /** 用户 ID */
    private String userId;

    /** 团队内角色：TEAM_ADMIN / EDITOR / VIEWER（兼容输入 MEMBER 为 EDITOR） */
    private String role;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
