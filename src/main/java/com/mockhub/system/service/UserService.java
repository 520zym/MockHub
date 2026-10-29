package com.mockhub.system.service;

import com.mockhub.system.model.entity.User;

/**
 * 用户服务接口
 * <p>
 * 提供用户 CRUD 及认证所需的查询方法。
 * 实现类负责业务逻辑校验和数据持久化。
 */
public interface UserService {

    /**
     * 通过用户名查找用户（登录时调用）
     *
     * @param username 用户名，区分大小写
     * @return 用户对象含 passwordHash，未找到时返回 null
     */
    User findByUsername(String username);

    /**
     * 通过 ID 获取用户
     *
     * @param id 用户 ID
     * @return 用户对象，未找到时返回 null
     */
    User findById(String id);

    /**
     * 更新用户的 firstLogin 标志
     *
     * @param userId     用户 ID
     * @param firstLogin 新值
     */
    void updateFirstLogin(String userId, boolean firstLogin);

    /**
     * 超级管理员重置用户密码，并要求用户下次登录修改密码。
     * 权限校验先于密码校验和目标用户查询。
     *
     * @param userId      目标用户 ID
     * @param newPassword 新密码（明文）
     */
    void resetPassword(String userId, String newPassword);

    /**
     * 更新用户密码
     *
     * @param userId          用户 ID
     * @param newPasswordHash BCrypt 哈希后的密码
     */
    void updatePassword(String userId, String newPasswordHash);
}
