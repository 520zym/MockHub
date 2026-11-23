package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.enums.TeamRoleType;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.model.entity.Team;
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

    @Autowired
    private TeamRepository teamRepository;

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
        TeamRoleType role = TeamRoleType.fromStored(userRepository.findUserTeamRole(userId, teamId));
        if (role == null) {
            log.warn("用户无权访问团队：userId={}, teamId={}", userId, teamId);
            throw new BizException(40102, "不能访问其他团队数据");
        }
    }

    @Override
    public void checkTeamWrite(String teamId) {
        if (SecurityContextUtil.isSuperAdmin()) { checkTeamWritable(teamId); return; }
        String userId = SecurityContextUtil.getCurrentUserId();
        TeamRoleType role = TeamRoleType.fromStored(userRepository.findUserTeamRole(userId, teamId));
        if (role == null) throw new BizException(40102, "不能访问其他团队数据");
        if (!role.canWrite()) throw new BizException(40104, "当前团队角色为只读，无法修改资源");
        checkTeamWritable(teamId);
    }

    @Override
    public void checkSystemAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) throw new BizException(40101, "仅超级管理员可执行此操作");
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
            checkTeamWritable(teamId);
            return;
        }

        String userId = SecurityContextUtil.getCurrentUserId();
        TeamRoleType role = TeamRoleType.fromStored(userRepository.findUserTeamRole(userId, teamId));
        if (role == null || !role.canAdminister()) {
            log.warn("用户非团队管理员：userId={}, teamId={}, role={}", userId, teamId, role);
            throw new BizException(40101, "无操作权限");
        }
        checkTeamWritable(teamId);
    }

    private void checkTeamWritable(String teamId) {
        Team team = teamRepository.findById(teamId);
        if (team == null) throw new BizException(40403, "团队不存在");
        if (team.isArchived()) throw new BizException(40903, "团队已归档，请由系统管理员恢复后再修改");
    }
}
