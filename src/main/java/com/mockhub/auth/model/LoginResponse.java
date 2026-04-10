package com.mockhub.auth.model;

/**
 * 登录响应 DTO
 * <p>
 * 对应接口：POST /api/auth/login 的 data 部分。
 * 包含 JWT Token 和用户信息，前端用于初始化登录状态。
 */
public class LoginResponse {

    /** JWT Token，前端存入 localStorage 并在后续请求中通过 Authorization 头携带 */
    private String token;

    /** 用户信息（含团队角色列表） */
    private UserInfo user;

    public LoginResponse() {
    }

    public LoginResponse(String token, UserInfo user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }
}
