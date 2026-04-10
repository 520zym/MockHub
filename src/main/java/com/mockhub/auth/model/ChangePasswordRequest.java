package com.mockhub.auth.model;

/**
 * 修改密码请求 DTO
 * <p>
 * 对应接口：POST /api/auth/change-password
 */
public class ChangePasswordRequest {

    /** 原密码（明文） */
    private String oldPassword;

    /** 新密码（明文） */
    private String newPassword;

    public ChangePasswordRequest() {
    }

    public ChangePasswordRequest(String oldPassword, String newPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
