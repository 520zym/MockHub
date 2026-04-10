package com.mockhub.system.service;

import com.mockhub.system.model.entity.Team;

import java.util.List;

/**
 * 团队服务接口
 * <p>
 * 提供团队 CRUD 及 Mock 分发所需的查询方法。
 * 实现类负责业务逻辑校验和数据持久化。
 */
public interface TeamService {

    /**
     * 通过团队短标识查找团队（Mock 分发时调用）
     *
     * @param identifier 团队短标识如 "FE"，匹配时不区分大小写
     * @return 匹配的团队，未找到时返回 null
     */
    Team findByIdentifier(String identifier);

    /**
     * 查询用户可见的所有团队
     * - 超级管理员：返回全部团队
     * - 其他用户：返回所属团队
     *
     * @param userId 用户 ID，不能为 null
     * @return 团队列表，无团队时返回空列表（不返回 null）
     */
    List<Team> findTeamsByUserId(String userId);

    /**
     * 获取团队详情
     *
     * @param teamId 团队 ID
     * @return 团队对象
     */
    Team getById(String teamId);
}
