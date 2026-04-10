package com.mockhub.system.controller;

import com.mockhub.common.model.Result;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.model.dto.AddMemberRequest;
import com.mockhub.system.model.dto.CreateTeamRequest;
import com.mockhub.system.model.dto.TeamMemberVO;
import com.mockhub.system.model.dto.UpdateMemberRoleRequest;
import com.mockhub.system.model.dto.UpdateTeamRequest;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队管理控制器
 * <p>
 * 提供团队 CRUD 和成员管理功能，仅超级管理员可访问。
 * 权限校验在 Service 层实现。
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    @Autowired
    private TeamServiceImpl teamService;

    /**
     * GET /api/teams — 团队列表
     * <p>
     * 超级管理员看到全部团队，普通用户只看到所属团队。
     *
     * @return 团队列表（含 memberCount 和 apiCount）
     */
    @GetMapping
    public Result<List<Team>> list() {
        String userId = SecurityContextUtil.getCurrentUserId();
        List<Team> teams = teamService.findTeamsByUserId(userId);
        return Result.ok(teams);
    }

    /**
     * POST /api/teams — 创建团队
     *
     * @param request 创建团队请求
     * @return 创建后的团队对象
     */
    @PostMapping
    public Result<Team> create(@RequestBody CreateTeamRequest request) {
        Team team = teamService.create(request);
        return Result.ok(team);
    }

    /**
     * PUT /api/teams/{id} — 修改团队
     *
     * @param id      团队 ID
     * @param request 修改请求
     * @return 修改后的团队对象
     */
    @PutMapping("/{id}")
    public Result<Team> update(@PathVariable String id, @RequestBody UpdateTeamRequest request) {
        Team team = teamService.update(id, request);
        return Result.ok(team);
    }

    /**
     * DELETE /api/teams/{id} — 删除团队
     *
     * @param id 团队 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        teamService.delete(id);
        return Result.ok();
    }

    /**
     * GET /api/teams/{id}/members — 团队成员列表
     *
     * @param id 团队 ID
     * @return 成员列表
     */
    @GetMapping("/{id}/members")
    public Result<List<TeamMemberVO>> listMembers(@PathVariable String id) {
        List<TeamMemberVO> members = teamService.listMembers(id);
        return Result.ok(members);
    }

    /**
     * POST /api/teams/{id}/members — 添加团队成员
     *
     * @param id      团队 ID
     * @param request 添加成员请求
     * @return 成功响应
     */
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable String id, @RequestBody AddMemberRequest request) {
        teamService.addMember(id, request);
        return Result.ok();
    }

    /**
     * DELETE /api/teams/{id}/members/{userId} — 移除团队成员
     *
     * @param id     团队 ID
     * @param userId 用户 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable String id, @PathVariable String userId) {
        teamService.removeMember(id, userId);
        return Result.ok();
    }

    /**
     * PUT /api/teams/{id}/members/{userId}/role — 修改成员角色
     *
     * @param id      团队 ID
     * @param userId  用户 ID
     * @param request 角色修改请求
     * @return 成功响应
     */
    @PutMapping("/{id}/members/{userId}/role")
    public Result<Void> updateMemberRole(@PathVariable String id, @PathVariable String userId,
                                         @RequestBody UpdateMemberRoleRequest request) {
        teamService.updateMemberRole(id, userId, request);
        return Result.ok();
    }
}
