package com.mockhub.auth.controller;

import com.mockhub.auth.model.ChangePasswordRequest;
import com.mockhub.auth.model.LoginRequest;
import com.mockhub.auth.model.LoginResponse;
import com.mockhub.auth.model.UserInfo;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import com.mockhub.auth.service.AuthService;
import com.mockhub.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：处理登录、登出、修改密码
 * <p>
 * 路由前缀：/api/auth
 * <ul>
 *   <li>POST /api/auth/login — 登录，返回 JWT Token 和用户信息</li>
 *   <li>POST /api/auth/logout — 撤销当前网页会话</li>
 *   <li>POST /api/auth/change-password — 修改密码（需登录态）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    /**
     * 登录
     * <p>
     * 校验用户名密码，成功后返回 JWT Token 和用户信息。
     * 如果 firstLogin=true，前端应强制跳转修改密码页。
     *
     * @param req 登录请求体（username, password）
     * @return Result 包含 token 和 user 信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        log.debug("收到登录请求 [username={}]", req.getUsername());
        LoginResponse response = authService.login(req, request.getHeader("User-Agent"), request.getRemoteAddr());
        return Result.ok(response);
    }

    /**
     * 登出
     * <p>
     * 服务端撤销当前 sid，不影响其他网页会话或 MCP 凭证。
     * 前端收到成功响应后清除 localStorage 中的 token 即可。
     *
     * @return Result 成功标识
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserInfo> me() { return Result.ok(authService.me()); }

    /**
     * 修改密码
     * <p>
     * 需要登录态（JWT 有效）。校验旧密码后更新为新密码。
     * 如果用户 firstLogin=true，修改成功后自动置为 false。
     *
     * @param req 修改密码请求体（oldPassword, newPassword）
     * @return Result 成功标识
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        log.debug("收到修改密码请求");
        authService.changePassword(req);
        return Result.ok();
    }
}
