package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.enums.UserRole;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户服务实现
 * <p>
 * 负责用户 CRUD 业务逻辑，包括：
 * - 创建用户（密码 BCrypt 加密，firstLogin=true）
 * - 修改用户信息
 * - 删除用户（不能删除超管）
 * - 分配团队角色
 * - 提供认证模块所需的查询方法
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

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
    public void updatePassword(String userId, String newPasswordHash) {
        log.info("更新用户密码：userId={}", userId);
        userRepository.updatePassword(userId, newPasswordHash, LocalDateTime.now().toString());
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

    /**
     * 创建新用户
     * <p>
     * 密码使用 BCrypt 加密，首次登录标志设为 true。
     *
     * @param request 创建用户请求
     * @return 创建后的用户对象
     * @throws BizException 40201 用户名已存在
     */
    public User create(CreateUserRequest request) {
        checkSuperAdmin();

        // 检查用户名唯一性
        User existing = userRepository.findByUsername(request.getUsername());
        if (existing != null) {
            throw new BizException(40201, "用户名已存在");
        }

        String now = LocalDateTime.now().toString();
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setGlobalRole(request.getGlobalRole());
        user.setFirstLogin(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        userRepository.insert(user);
        log.info("创建用户成功：username={}, globalRole={}", user.getUsername(), user.getGlobalRole());
        return user;
    }

    /**
     * 修改用户信息（不含密码和团队分配）
     *
     * @param id      用户 ID
     * @param request 修改请求
     * @return 修改后的用户对象
     * @throws BizException 40203 不能降级超级管理员
     */
    public User update(String id, UpdateUserRequest request) {
        checkSuperAdmin();

        User user = userRepository.findById(id);
        if (user == null) {
            throw new BizException(40201, "用户不存在");
        }

        // 不能降级超级管理员
        if (UserRole.SUPER_ADMIN.name().equals(user.getGlobalRole())
                && !UserRole.SUPER_ADMIN.name().equals(request.getGlobalRole())) {
            throw new BizException(40203, "不能降级超级管理员");
        }

        user.setDisplayName(request.getDisplayName());
        user.setGlobalRole(request.getGlobalRole());
        user.setUpdatedAt(LocalDateTime.now().toString());

        userRepository.update(user);
        log.info("修改用户成功：id={}, displayName={}, globalRole={}", id, request.getDisplayName(), request.getGlobalRole());
        return user;
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     * @throws BizException 40202 不能删除超级管理员
     */
    public void delete(String id) {
        checkSuperAdmin();

        User user = userRepository.findById(id);
        if (user == null) {
            throw new BizException(40201, "用户不存在");
        }

        // 不能删除超级管理员
        if (UserRole.SUPER_ADMIN.name().equals(user.getGlobalRole())) {
            throw new BizException(40202, "不能删除超级管理员");
        }

        userRepository.deleteById(id);
        log.info("删除用户成功：id={}, username={}", id, user.getUsername());
    }

    /**
     * 为用户分配团队角色（整体替换）
     *
     * @param userId  用户 ID
     * @param request 团队角色分配请求
     * @throws BizException 40201 用户不存在
     */
    public void assignTeams(String userId, AssignTeamsRequest request) {
        checkSuperAdmin();

        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BizException(40201, "用户不存在");
        }

        List<UserTeam> teamRoles = new ArrayList<UserTeam>();
        if (request.getTeamRoles() != null) {
            for (AssignTeamsRequest.TeamRoleItem item : request.getTeamRoles()) {
                teamRoles.add(new UserTeam(userId, item.getTeamId(), item.getRole()));
            }
        }

        userRepository.replaceTeamRoles(userId, teamRoles);
        log.info("用户团队分配成功：userId={}, teamCount={}", userId, teamRoles.size());
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
