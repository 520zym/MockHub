package com.mockhub.mock.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 接口分组 Service
 * <p>
 * 提供分组的 CRUD 操作，删除分组时自动将关联接口的 groupId 置为 null。
 */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final ApiRepository apiRepository;
    private final PermissionChecker permissionChecker;

    public GroupService(GroupRepository groupRepository,
                        ApiRepository apiRepository,
                        PermissionChecker permissionChecker) {
        this.groupRepository = groupRepository;
        this.apiRepository = apiRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 查询团队的所有分组（按 sortOrder 升序排列）
     * <p>
     * 同时填充每个分组下的接口数量。
     *
     * @param teamId 团队 ID
     * @return 分组列表
     */
    public List<ApiGroup> findByTeamId(String teamId) {
        permissionChecker.checkTeamAccess(teamId);

        List<ApiGroup> groups = groupRepository.findByTeamId(teamId);

        // 填充每个分组下的接口数量
        for (ApiGroup group : groups) {
            long count = apiRepository.countByGroupId(group.getId());
            group.setApiCount((int) count);
        }

        return groups;
    }

    /**
     * 创建分组
     *
     * @param group 分组对象（需包含 teamId 和 name）
     * @return 创建后的分组对象（含生成的 ID 和创建时间）
     */
    public ApiGroup create(ApiGroup group) {
        permissionChecker.checkTeamAccess(group.getTeamId());

        group.setId(UUID.randomUUID().toString());
        group.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));

        groupRepository.insert(group);
        log.info("创建分组: id={}, teamId={}, name={}", group.getId(), group.getTeamId(), group.getName());

        return group;
    }

    /**
     * 更新分组
     *
     * @param id    分组 ID
     * @param group 更新内容（name、sortOrder）
     * @return 更新后的分组对象
     */
    public ApiGroup update(String id, ApiGroup group) {
        ApiGroup existing = groupRepository.findById(id);
        if (existing == null) {
            throw new BizException(40501, "分组不存在");
        }

        permissionChecker.checkTeamAccess(existing.getTeamId());

        existing.setName(group.getName());
        existing.setSortOrder(group.getSortOrder());

        groupRepository.update(existing);
        log.info("更新分组: id={}, name={}, sortOrder={}", id, existing.getName(), existing.getSortOrder());

        return existing;
    }

    /**
     * 删除分组
     * <p>
     * 删除前将该分组下所有接口的 groupId 置为 null（变为未分组）。
     *
     * @param id 分组 ID
     */
    public void delete(String id) {
        ApiGroup group = groupRepository.findById(id);
        if (group == null) {
            throw new BizException(40501, "分组不存在");
        }

        permissionChecker.checkTeamAccess(group.getTeamId());

        // 将关联接口的 groupId 置为 null
        apiRepository.clearGroupId(id);
        log.info("清除分组关联: groupId={}", id);

        groupRepository.deleteById(id);
        log.info("删除分组: id={}, name={}", id, group.getName());
    }
}
