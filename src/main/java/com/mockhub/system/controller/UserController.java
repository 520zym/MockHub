package com.mockhub.system.controller;

import com.mockhub.common.model.Result;
import com.mockhub.system.model.dto.AssignTeamsRequest;
import com.mockhub.system.model.dto.CreateUserRequest;
import com.mockhub.system.model.dto.UpdateUserRequest;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.model.entity.UserTeam;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 * <p>
 * 提供用户 CRUD 和团队分配功能，仅超级管理员可访问。
 * 权限校验在 Service 层实现。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private TeamRepository teamRepository;

    /**
     * GET /api/users — 用户列表（不分页）
     *
     * @return 用户列表（含团队角色信息）
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<User> users = userService.listAll();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

        for (User user : users) {
            result.add(buildUserVO(user));
        }

        return Result.ok(result);
    }

    /**
     * POST /api/users — 创建用户
     *
     * @param request 创建用户请求
     * @return 创建后的用户对象
     */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateUserRequest request) {
        User user = userService.create(request);
        return Result.ok(buildUserVO(user));
    }

    /**
     * PUT /api/users/{id} — 修改用户信息
     *
     * @param id      用户 ID
     * @param request 修改请求
     * @return 修改后的用户对象
     */
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        User user = userService.update(id, request);
        return Result.ok(buildUserVO(user));
    }

    /**
     * DELETE /api/users/{id} — 删除用户
     *
     * @param id 用户 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return Result.ok();
    }

    /**
     * POST /api/users/{id}/teams — 为用户分配团队（整体替换）
     *
     * @param id      用户 ID
     * @param request 团队角色分配请求
     * @return 成功响应
     */
    @PostMapping("/{id}/teams")
    public Result<Void> assignTeams(@PathVariable String id, @RequestBody AssignTeamsRequest request) {
        userService.assignTeams(id, request);
        return Result.ok();
    }

    /**
     * 构建用户视图对象（含团队角色信息）
     *
     * @param user 用户实体
     * @return 视图 Map
     */
    private Map<String, Object> buildUserVO(User user) {
        Map<String, Object> vo = new HashMap<String, Object>();
        vo.put("id", user.getId());
        vo.put("username", user.getUsername());
        vo.put("displayName", user.getDisplayName());
        vo.put("globalRole", user.getGlobalRole());
        vo.put("firstLogin", user.isFirstLogin());
        vo.put("createdAt", user.getCreatedAt());

        // 填充团队角色信息
        List<UserTeam> teamRoles = userService.findTeamRoles(user.getId());
        List<Map<String, Object>> teams = new ArrayList<Map<String, Object>>();
        for (UserTeam ut : teamRoles) {
            Map<String, Object> teamInfo = new HashMap<String, Object>();
            teamInfo.put("teamId", ut.getTeamId());
            teamInfo.put("role", ut.getRole());

            // 查询团队名称和标识
            Team team = teamRepository.findById(ut.getTeamId());
            if (team != null) {
                teamInfo.put("teamName", team.getName());
                teamInfo.put("identifier", team.getIdentifier());
            }
            teams.add(teamInfo);
        }
        vo.put("teams", teams);

        return vo;
    }
}
