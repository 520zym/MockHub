package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.auth.service.CredentialRevocationService;
import com.mockhub.common.util.PasswordUtil;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.model.dto.AssignTeamsRequest;
import com.mockhub.system.model.dto.CreateUserRequest;
import com.mockhub.system.model.dto.UpdateUserRequest;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.model.entity.UserTeam;
import com.mockhub.system.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 用户服务实现
 * <p>
 * 负责用户 CRUD 业务逻辑，包括：
 * - 创建用户（密码 BCrypt 加密，firstLogin=true）
 * - 修改用户信息
 * - 停用、恢复及物理删除用户（末位有效管理员保护）
 * - 分配团队角色
 * - 提供认证模块所需的查询方法
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CredentialRevocationService credentialRevocationService;

    @Autowired private AdminSafetyService adminSafetyService;
    @Autowired private com.mockhub.system.repository.TeamRepository teamRepository;

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public User findById(String id) {
        return userRepository.findById(id);
    }

    @Override
    public void updateFirstLogin(String userId, boolean firstLogin) {
        log.info("更新用户首次登录标志：userId={}, firstLogin={}", userId, firstLogin);
        userRepository.updateFirstLogin(userId, firstLogin, LocalDateTime.now().toString());
    }

    @Override
    @Transactional
    public void updatePassword(String userId, String newPasswordHash) {
        log.info("更新用户密码：userId={}", userId);
        userRepository.updatePassword(userId, newPasswordHash, LocalDateTime.now().toString());
        credentialRevocationService.revokeSecurityCredentials(userId);
    }

    @Override
    @Transactional
    public void resetPassword(String userId, String newPassword) {
        checkSuperAdmin();

        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(40000, "新密码长度不能少于6位");
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BizException(40401, "用户不存在");
        }

        String newHash = PasswordUtil.hash(newPassword);
        String now = LocalDateTime.now().toString();
        userRepository.updatePassword(userId, newHash, now);
        userRepository.updateFirstLogin(userId, true, now);
        credentialRevocationService.revokeSecurityCredentials(userId);
        log.info("超管重置用户密码：userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 查询所有用户列表（含团队角色信息）
     * <p>
     * 仅超级管理员可调用。
     *
     * @return 用户列表
     */
    public List<User> listAll() {
        checkSuperAdmin();
        log.info("查询所有用户列表");
        return userRepository.findAll();
    }

    /**
     * 查询用户的团队角色列表
     *
     * @param userId 用户 ID
     * @return 团队角色关联列表
     */
    public List<UserTeam> findTeamRoles(String userId) {
        return userRepository.findTeamRolesByUserId(userId);
    }

    /** Platform account creation is never delegated to team administrators. */
    @Transactional
    public User create(CreateUserRequest request) {
        adminSafetyService.acquireWriteLock();
        checkSuperAdmin();
        if (request == null || request.getUsername() == null || request.getUsername().trim().isEmpty())
            throw new BizException(40000, "用户名不能为空");
        if (request.getPassword() == null || request.getPassword().length() < 6)
            throw new BizException(40000, "密码长度不能少于6位");
        String role = RoleInputs.globalRole(request.getGlobalRole() == null ? "USER" : request.getGlobalRole());
        if (userRepository.findByUsername(request.getUsername()) != null) throw new BizException(40201, "用户名已存在");
        String now = LocalDateTime.now().toString();
        User user = new User();
        user.setId(UUID.randomUUID().toString()); user.setUsername(request.getUsername());
        user.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        user.setDisplayName(request.getDisplayName() == null ? request.getUsername() : request.getDisplayName());
        user.setGlobalRole(role); user.setFirstLogin(true); user.setStatus("ACTIVE");
        user.setCreatedAt(now); user.setUpdatedAt(now);
        userRepository.insert(user);
        return user;
    }

    @Transactional
    public User update(String id, UpdateUserRequest request) {
        adminSafetyService.acquireWriteLock();
        checkSuperAdmin();
        User user = requireUser(id);
        if (request == null) throw new BizException(40000, "请求不能为空");
        String oldRole = user.getGlobalRole();
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getGlobalRole() != null) user.setGlobalRole(RoleInputs.globalRole(request.getGlobalRole()));
        user.setUpdatedAt(LocalDateTime.now().toString());
        userRepository.update(user);
        if ("SUPER_ADMIN".equals(oldRole)) adminSafetyService.validateSuperAdmins();
        if (!oldRole.equals(user.getGlobalRole())) credentialRevocationService.revokeSecurityCredentials(id);
        return userRepository.findById(id);
    }

    /** Prefer DISABLED. Physical deletion explicitly detaches FK references and preserves resources/audits. */
    @Transactional
    public void delete(String id) {
        adminSafetyService.acquireWriteLock();
        checkSuperAdmin();
        User user = requireUser(id);
        List<String> affectedTeams = adminSafetyService.effectiveAdminTeams(id);
        // Revoke before deletion so failure rolls everything back and no usable credential survives.
        credentialRevocationService.revokeSecurityCredentials(id);
        adminSafetyService.detachDeletedUser(id);
        userRepository.deleteById(id);
        adminSafetyService.validateTeams(affectedTeams);
        if ("SUPER_ADMIN".equals(user.getGlobalRole())) adminSafetyService.validateSuperAdmins();
    }

    @Override
    @Transactional
    public User updateStatus(String id, com.mockhub.system.model.dto.UpdateUserStatusRequest request) {
        adminSafetyService.acquireWriteLock();
        checkSuperAdmin();
        User user = requireUser(id);
        String status = request == null ? null : request.getStatus();
        if (!("ACTIVE".equals(status) || "DISABLED".equals(status))) throw new BizException(40000, "未知账号状态");
        List<String> affectedTeams = adminSafetyService.effectiveAdminTeams(id);
        userRepository.updateStatus(id, status, LocalDateTime.now().toString());
        adminSafetyService.validateTeams(affectedTeams);
        if ("SUPER_ADMIN".equals(user.getGlobalRole())) adminSafetyService.validateSuperAdmins();
        // Reactivation is also a security event. It must never resurrect a previous session or MCP token.
        credentialRevocationService.revokeSecurityCredentials(id);
        return userRepository.findById(id);
    }

    /** Whole-request validation precedes mutation, final administrator validation follows mutation. */
    @Transactional
    public void assignTeams(String userId, AssignTeamsRequest request) {
        adminSafetyService.acquireWriteLock();
        checkSuperAdmin();
        User user = requireUser(userId);
        if (request == null || request.getTeamRoles() == null) throw new BizException(40000, "必须提供团队角色列表（清空请传空数组）");
        java.util.Map<String,String> desired = new java.util.LinkedHashMap<String,String>();
        for (AssignTeamsRequest.TeamRoleItem item : request.getTeamRoles()) {
            if (item == null || item.getTeamId() == null || item.getTeamId().trim().isEmpty()) throw new BizException(40000, "团队ID不能为空");
            if (desired.containsKey(item.getTeamId())) throw new BizException(40000, "团队ID不能重复");
            String role = RoleInputs.teamRole(item.getRole());
            desired.put(item.getTeamId(), role);
        }
        java.util.Map<String,String> previous = new java.util.LinkedHashMap<String,String>();
        for (UserTeam old : userRepository.findTeamRolesByUserId(userId)) previous.put(old.getTeamId(), old.getRole());
        if (!user.isActive()) {
            // A status change retains memberships for later reactivation. Keeping that same admin
            // relationship is not a new grant, including when its team has since been archived.
            for (java.util.Map.Entry<String,String> item : desired.entrySet()) {
                if ("TEAM_ADMIN".equals(item.getValue()) && !"TEAM_ADMIN".equals(previous.get(item.getKey()))) {
                    throw new BizException(40000, "不能为停用账号新增或提升团队管理员角色");
                }
            }
        }
        java.util.Set<String> changed = new java.util.LinkedHashSet<String>();
        changed.addAll(previous.keySet()); changed.addAll(desired.keySet());
        for (String teamId : desired.keySet()) {
            if (teamRepository.findById(teamId) == null) throw new BizException(40403, "团队不存在");
        }
        for (String teamId : changed) {
            if (java.util.Objects.equals(previous.get(teamId), desired.get(teamId))) continue;
            com.mockhub.system.model.entity.Team team = teamRepository.findById(teamId);
            if (team != null && team.isArchived()) throw new BizException(40906, "团队已归档，不能修改成员分配");
        }
        List<String> affectedTeams = adminSafetyService.effectiveAdminTeams(userId);
        for (String teamId : changed) {
            String old = previous.get(teamId), role = desired.get(teamId);
            if (java.util.Objects.equals(old, role)) continue;
            if (role == null) teamRepository.removeMember(teamId, userId);
            else if (old == null) teamRepository.addMember(teamId, userId, role);
            else teamRepository.updateMemberRole(teamId, userId, role);
        }
        adminSafetyService.validateTeams(affectedTeams);
    }

    private User requireUser(String id) {
        User user = userRepository.findById(id);
        if (user == null) throw new BizException(40401, "用户不存在");
        return user;
    }

    /**
     * 校验当前用户是否为超级管理员，不是则抛出异常
     */
    private void checkSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40101, "无操作权限");
        }
    }
}
