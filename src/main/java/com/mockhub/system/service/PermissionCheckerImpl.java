package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 权限校验实现
 * <p>
 * 实现 common 模块定义的 PermissionChecker 接口。
 * 各模块 Service 层通过注入此接口进行团队级别的权限校验。
 */
@Component
public class PermissionCheckerImpl implements PermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckerImpl.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * 校验当前用户是否有权访问目标团队
     * <p>
     * - 超级管理员：直接通过
     * - 其他用户：检查 user_team 关联表
     *
     * @param teamId 目标团队 ID
     * @throws BizException code=40102，无权访问时抛出
     */
    @Override
    public void checkTeamAccess(String teamId) {
        if (SecurityContextUtil.isSuperAdmin()) {
            return;
        }

        String userId = SecurityContextUtil.getCurrentUserId();
        boolean exists = userRepository.existsUserTeam(userId, teamId);
        if (!exists) {
            log.warn("用户无权访问团队：userId={}, teamId={}", userId, teamId);
            throw new BizException(40102, "不能访问其他团队数据");
        }
    }

    /**
     * 校验当前用户是否为目标团队的管理员（或超级管理员）
     * <p>
     * - 超级管理员：直接通过
     * - 团队管理员：检查 user_team 关联表且 role=TEAM_ADMIN
     * - 其他：抛出异常
     *
     * @param teamId 目标团队 ID
     * @throws BizException code=40101，无操作权限时抛出
     */
    @Override
    public void checkTeamAdmin(String teamId) {
        if (SecurityContextUtil.isSuperAdmin()) {
            return;
        }

        String userId = SecurityContextUtil.getCurrentUserId();
        String role = userRepository.findUserTeamRole(userId, teamId);
        if (!"TEAM_ADMIN".equals(role)) {
            log.warn("用户非团队管理员：userId={}, teamId={}, role={}", userId, teamId, role);
            throw new BizException(40101, "无操作权限");
        }
    }
}
