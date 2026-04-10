package com.mockhub.mock.controller;

import com.mockhub.common.model.Result;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.service.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 接口分组管理 Controller
 * <p>
 * 提供分组的 CRUD 端点。
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * 查询团队的分组列表（按 sortOrder 排序）
     *
     * @param teamId 团队 ID
     * @return 分组列表（含每个分组下的接口数量）
     */
    @GetMapping
    public Result<List<ApiGroup>> list(@RequestParam String teamId) {
        List<ApiGroup> groups = groupService.findByTeamId(teamId);
        return Result.ok(groups);
    }

    /**
     * 创建分组
     *
     * @param group 分组对象（需包含 teamId、name）
     * @return 创建后的分组对象
     */
    @PostMapping
    public Result<ApiGroup> create(@RequestBody ApiGroup group) {
        ApiGroup created = groupService.create(group);
        return Result.ok(created);
    }

    /**
     * 更新分组
     *
     * @param id    分组 ID
     * @param group 更新内容（name、sortOrder）
     * @return 更新后的分组对象
     */
    @PutMapping("/{id}")
    public Result<ApiGroup> update(@PathVariable String id, @RequestBody ApiGroup group) {
        ApiGroup updated = groupService.update(id, group);
        return Result.ok(updated);
    }

    /**
     * 删除分组
     * <p>
     * 分组下的接口 groupId 自动置为 null（变为未分组）。
     *
     * @param id 分组 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        groupService.delete(id);
        return Result.ok();
    }
}
