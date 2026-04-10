package com.mockhub.auth.service;

import com.mockhub.auth.model.ChangePasswordRequest;
import com.mockhub.auth.model.LoginRequest;
import com.mockhub.auth.model.LoginResponse;
import com.mockhub.auth.model.TeamRole;
import com.mockhub.auth.model.UserInfo;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.common.util.PasswordUtil;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.service.TeamService;
import com.mockhub.system.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 认证服务：处理登录、登出、修改密码等认证相关业务逻辑
 * <p>
 * 面向接口编码，依赖 system 模块的 UserService 和 TeamService。
 * 这两个接口由 system 模块实现并注册为 Spring Bean，此处通过 @Autowired 注入。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户登录
     * <p>
     * 流程：
     * 1. 通过用户名查找用户
     * 2. 校验密码（BCrypt）
     * 3. 查询用户所属团队列表
     * 4. 生成 JWT Token
     * 5. 组装 LoginResponse 返回
     *
     * @param req 登录请求（包含用户名和密码）
     * @return 登录响应（包含 Token 和用户信息）
     * @throws BizException code=40001，用户名或密码错误
     */
    public LoginResponse login(LoginRequest req) {
        String username = req.getUsername();

        // 1. 查找用户
        User user = userService.findByUsername(username);
        if (user == null) {
            log.warn("登录失败：用户不存在 [username={}]", username);
            throw new BizException(40001, "用户名或密码错误");
        }

        // 2. 校验密码
        if (!PasswordUtil.verify(req.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败：密码错误 [username={}]", username);
            throw new BizException(40001, "用户名或密码错误");
        }

        // 3. 查询用户所属团队列表
        List<Team> teams = teamService.findTeamsByUserId(user.getId());
        List<TeamRole> teamRoles = buildTeamRoles(user, teams);

        // 4. 提取团队 ID 列表，用于写入 JWT
        List<String> teamIds = new ArrayList<String>();
        for (Team team : teams) {
            teamIds.add(team.getId());
        }

        // 5. 生成 JWT Token
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getGlobalRole(),
                teamIds
        );

        // 6. 组装用户信息
        UserInfo userInfo = new UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setDisplayName(user.getDisplayName());
        userInfo.setGlobalRole(user.getGlobalRole());
        userInfo.setFirstLogin(user.isFirstLogin());
        userInfo.setTeams(teamRoles);

        log.info("登录成功 [username={}, userId={}, firstLogin={}]",
                username, user.getId(), user.isFirstLogin());

        return new LoginResponse(token, userInfo);
    }

    /**
     * 修改密码
     * <p>
     * 流程：
     * 1. 从 SecurityContext 获取当前用户 ID
     * 2. 查询用户信息
     * 3. 校验旧密码
     * 4. 用 BCrypt 加密新密码并更新
     * 5. 如果是首次登录，将 firstLogin 置为 false
     *
     * @param req 修改密码请求（包含旧密码和新密码）
     * @throws BizException code=40004，原密码错误
     */
    public void changePassword(ChangePasswordRequest req) {
        // 1. 获取当前登录用户 ID
        String userId = SecurityContextUtil.getCurrentUserId();

        // 2. 查询用户完整信息（需要 passwordHash 用于校验旧密码）
        User user = userService.findById(userId);
        if (user == null) {
            // 正常情况不应出现：已登录用户在数据中不存在
            log.error("修改密码异常：用户不存在 [userId={}]", userId);
            throw new BizException(50001, "系统内部错误");
        }

        // 3. 校验旧密码
        if (!PasswordUtil.verify(req.getOldPassword(), user.getPasswordHash())) {
            log.warn("修改密码失败：原密码错误 [userId={}, username={}]", userId, user.getUsername());
            throw new BizException(40004, "原密码错误");
        }

        // 4. 用 BCrypt 加密新密码并更新
        String newPasswordHash = PasswordUtil.hash(req.getNewPassword());
        userService.updatePassword(userId, newPasswordHash);

        // 5. 如果是首次登录，将 firstLogin 标志置为 false
        if (user.isFirstLogin()) {
            userService.updateFirstLogin(userId, false);
            log.info("首次登录密码修改完成，已将 firstLogin 置为 false [userId={}, username={}]",
                    userId, user.getUsername());
        }

        log.info("密码修改成功 [userId={}, username={}]", userId, user.getUsername());
    }

    /**
     * 构建用户的团队角色列表
     * <p>
     * 将 Team 实体列表与用户的 teamRoles Map 合并，
     * 组装前端所需的 TeamRole DTO 列表。
     *
     * @param user  用户实体（包含 teamRoles Map：teamId → 角色）
     * @param teams 用户所属团队列表
     * @return 团队角色 DTO 列表
     */
    private List<TeamRole> buildTeamRoles(User user, List<Team> teams) {
        List<TeamRole> result = new ArrayList<TeamRole>();
        // 获取用户在各团队中的角色映射
        Map<String, String> userTeamRoles = user.getTeamRoles();

        for (Team team : teams) {
            String role = "";
            // 从用户的 teamRoles Map 中获取该团队的角色
            if (userTeamRoles != null && userTeamRoles.containsKey(team.getId())) {
                role = userTeamRoles.get(team.getId());
            }
            result.add(new TeamRole(
                    team.getId(),
                    team.getName(),
                    team.getIdentifier(),
                    role
            ));
        }
        return result;
    }
}
