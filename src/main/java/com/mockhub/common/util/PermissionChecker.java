package com.mockhub.common.util;

import com.mockhub.common.model.BizException;

/**
 * 权限校验接口
 * <p>
 * 定义在 common 模块，实现类由 system 模块提供。
 * Service 层通过注入此接口进行团队级别的权限校验。
 */
public interface PermissionChecker {

    /**
     * 校验当前用户是否有权访问目标团队
     * - 超级管理员：直接通过
     * - 其他用户：检查 user_team 关联
     *
     * @param teamId 目标团队 ID
     * @throws BizException code=40102，无权访问时抛出
     */
    void checkTeamAccess(String teamId);

    /**
     * 校验当前用户是否为目标团队的管理员（或超级管理员）
     *
     * @param teamId 目标团队 ID
     * @throws BizException code=40101，无操作权限时抛出
     */
    void checkTeamAdmin(String teamId);
}
