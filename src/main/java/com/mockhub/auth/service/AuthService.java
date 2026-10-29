package com.mockhub.auth.service;

import com.mockhub.auth.model.*;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.common.util.PasswordUtil;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.model.entity.UserTeam;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Current database identity and revocable web sessions, not JWT authorization snapshots. */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private AuthSessionRepository sessions;
    @Autowired private JwtUtil jwtUtil;

    @Transactional
    public LoginResponse login(LoginRequest req) { return login(req, null, null); }

    @Transactional
    public LoginResponse login(LoginRequest req, String userAgent, String ipAddress) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            throw new BizException(40001, "用户名或密码错误");
        }
        User user = userRepository.findByUsername(req.getUsername());
        if (user == null || !user.isActive() || user.getAuthVersion() < 0
                || !PasswordUtil.verify(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(40001, "用户名或密码错误或账号已停用");
        }
        UserInfo info = buildUserInfo(user);
        long now = System.currentTimeMillis();
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID().toString()); session.setUserId(user.getId());
        session.setAuthVersion(user.getAuthVersion()); session.setCreatedAt(Instant.ofEpochMilli(now).toString());
        session.setExpiresAt(now + JwtUtil.EXPIRATION_MS);
        session.setUserAgent(truncate(userAgent, 512)); session.setIpAddress(truncate(ipAddress, 64));
        if (!sessions.insertIfCurrent(session)) {
            throw new BizException(40002, "账号凭证已改变，请重新登录");
        }
        List<String> teamIds = new ArrayList<String>();
        for (TeamRole team : info.getTeams()) teamIds.add(team.getTeamId());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), info.getGlobalRole(),
                teamIds, user.getAuthVersion(), session.getId());
        return new LoginResponse(token, info);
    }

    public UserInfo me() { return buildUserInfo(requireActiveUser()); }

    @Transactional
    public UserInfo updateProfile(UpdateProfileRequest req) {
        String name = req == null ? null : req.getDisplayName();
        if (name == null || name.trim().isEmpty() || name.trim().length() > 64) {
            throw new BizException(40000, "显示名须为1至64个字符");
        }
        User user = requireActiveUser();
        userRepository.updateDisplayName(user.getId(), name.trim(), Instant.now().toString());
        return me();
    }

    public List<WebSessionInfo> listSessions() {
        String userId = requireActiveUser().getId();
        String currentSid = SecurityContextUtil.getCurrentSessionId();
        List<WebSessionInfo> result = new ArrayList<WebSessionInfo>();
        for (AuthSession session : sessions.findActiveByUser(userId, System.currentTimeMillis())) {
            result.add(new WebSessionInfo(session, currentSid));
        }
        return result;
    }

    public void logout() {
        sessions.revoke(SecurityContextUtil.getCurrentSessionId(), SecurityContextUtil.getCurrentUserId(),
                Instant.now().toString());
    }

    public void revokeSession(String id) {
        if (sessions.revoke(id, SecurityContextUtil.getCurrentUserId(), Instant.now().toString()) == 0) {
            throw new BizException(40401, "会话不存在或已撤销");
        }
    }

    /** All web logins only: deliberately leaves external MCP tokens valid. */
    @Transactional
    public void revokeWebSessions() {
        String userId = requireActiveUser().getId();
        String now = Instant.now().toString();
        if (userRepository.incrementAuthVersion(userId, now) != 1) {
            throw new BizException(40002, "账号不存在，请重新登录");
        }
        sessions.revokeAllByUser(userId, now);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        if (req == null || req.getOldPassword() == null || req.getNewPassword() == null
                || req.getNewPassword().length() < 6) {
            throw new BizException(40000, "新密码长度不能少于6位，且须提供原密码");
        }
        // 1. 获取当前登录用户 ID
        String userId = SecurityContextUtil.getCurrentUserId();

        // 2. 查询用户完整信息（需要 passwordHash 用于校验旧密码）
        User user = userService.findById(userId);
        if (user == null || !user.isActive()) {
            throw new BizException(40002, "账号不存在或已停用，请重新登录");
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


    private User requireActiveUser() {
        User user = userRepository.findById(SecurityContextUtil.getCurrentUserId());
        if (user == null || !user.isActive()) throw new BizException(40002, "账号不存在或已停用，请重新登录");
        return user;
    }

    private UserInfo buildUserInfo(User user) {
        String globalRole = user.getGlobalRole();
        if ("MEMBER".equals(globalRole) || "TEAM_ADMIN".equals(globalRole)) globalRole = "USER";
        if (!("USER".equals(globalRole) || "SUPER_ADMIN".equals(globalRole))) {
            throw new BizException(40002, "账号身份无效，请重新登录");
        }
        boolean systemAdmin = "SUPER_ADMIN".equals(globalRole);
        Map<String, String> membership = new HashMap<String, String>();
        for (UserTeam relation : userRepository.findTeamRolesByUserId(user.getId())) {
            String role = "MEMBER".equals(relation.getRole()) ? "EDITOR" : relation.getRole();
            if ("VIEWER".equals(role) || "EDITOR".equals(role) || "TEAM_ADMIN".equals(role)) {
                membership.put(relation.getTeamId(), role);
            }
        }
        List<Team> teams = systemAdmin ? teamRepository.findAll() : teamRepository.findByUserId(user.getId());
        List<TeamRole> teamRoles = new ArrayList<TeamRole>();
        for (Team team : teams) {
            String role = membership.get(team.getId());
            if (role == null && !systemAdmin) continue; // Unknown membership roles never gain access.
            TeamRole item = new TeamRole(team.getId(), team.getName(), team.getIdentifier(), role);
            item.setMembershipRole(role);
            item.setAccessMode(role == null ? "SYSTEM_MAINTENANCE" : "MEMBER");
            item.setTeamStatus(team.getStatus());
            boolean admin = !team.isArchived() && (systemAdmin || "TEAM_ADMIN".equals(role));
            boolean write = !team.isArchived() && (admin || "EDITOR".equals(role));
            Map<String, Boolean> capabilities = new LinkedHashMap<String, Boolean>();
            capabilities.put("read", true); capabilities.put("write", write);
            capabilities.put("manageMembers", admin); capabilities.put("manageTeam", admin);
            capabilities.put("purge", admin); capabilities.put("overwriteImport", admin);
            capabilities.put("batchDelete", admin);
            item.setCapabilities(capabilities); teamRoles.add(item);
        }
        UserInfo info = new UserInfo();
        info.setId(user.getId()); info.setUsername(user.getUsername()); info.setDisplayName(user.getDisplayName());
        info.setGlobalRole(globalRole); info.setFirstLogin(user.isFirstLogin()); info.setStatus(user.getStatus());
        info.setTeams(teamRoles);
        return info;
    }

    private String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
}
