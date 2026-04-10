package com.mockhub.system.model.dto;

/**
 * 修改团队成员角色请求体
 */
public class UpdateMemberRoleRequest {

    /** 团队内角色：TEAM_ADMIN / MEMBER */
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
