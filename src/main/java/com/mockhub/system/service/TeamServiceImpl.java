package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.model.dto.*;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Team administration is scoped to real team membership; platform accounts remain super-admin only. */
@Service
public class TeamServiceImpl implements TeamService {
    @Autowired private TeamRepository teamRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PermissionChecker permissionChecker;
    @Autowired private AdminSafetyService adminSafetyService;

    @Override
    public Team findByIdentifier(String identifier) { return teamRepository.findByIdentifier(identifier); }

    @Override
    public List<Team> findTeamsByUserId(String userId) {
        if (userId == null) return Collections.emptyList();
        User user = userRepository.findById(userId);
        if (user == null || !user.isActive()) return Collections.emptyList();
        boolean superAdmin = "SUPER_ADMIN".equals(user.getGlobalRole());
        List<Team> teams = superAdmin ? teamRepository.findAll() : teamRepository.findByUserId(userId);
        for (Team team : teams) fillCounts(team, superAdmin);
        return teams;
    }

    @Override
    public Team getById(String teamId) {
        Team team = teamRepository.findById(teamId);
        if (team == null) throw new BizException(40403, "团队不存在");
        return team;
    }

    public List<Team> listAll() {
        checkSuperAdmin();
        List<Team> teams = teamRepository.findAll();
        for (Team team : teams) fillCounts(team, true);
        return teams;
    }

    private void fillCounts(Team team, boolean superAdmin) {
        team.setMemberCount(teamRepository.countMembersByTeamId(team.getId()));
        team.setApiCount(teamRepository.countApisByTeamId(team.getId()));
        if (superAdmin) {
            int count = adminSafetyService.effectiveTeamAdminCount(team.getId());
            team.setEffectiveAdminCount(count);
            team.setAdminRepairRequired(count == 0);
        }
    }

    @Transactional
    public Team create(CreateTeamRequest request) {
        adminSafetyService.acquireWriteLock();
        checkSuperAdmin();
        if (request == null || request.getName() == null || request.getName().trim().isEmpty())
            throw new BizException(40000, "团队名称不能为空");
        if (request.getIdentifier() == null || !request.getIdentifier().matches("[A-Z0-9]{2,8}"))
            throw new BizException(40000, "团队标识须为2至8位大写字母或数字");
        if (request.getInitialAdminUserId() == null || request.getInitialAdminUserId().trim().isEmpty())
            throw new BizException(40000, "必须指定初始团队管理员");
        requireActiveUser(request.getInitialAdminUserId());
        if (teamRepository.findByName(request.getName()) != null) throw new BizException(40301, "团队名称已存在");
        if (teamRepository.findByIdentifier(request.getIdentifier()) != null) throw new BizException(40302, "团队标识已存在");
        Team team = new Team();
        team.setId(UUID.randomUUID().toString());
        team.setName(request.getName()); team.setIdentifier(request.getIdentifier());
        team.setColor(request.getColor() == null ? "#185FA5" : request.getColor());
        team.setDescription(request.getDescription()); team.setCreatedBy(SecurityContextUtil.getCurrentUserId());
        team.setCreatedAt(LocalDateTime.now().toString());
        teamRepository.insert(team);
        teamRepository.addMember(team.getId(), request.getInitialAdminUserId(), "TEAM_ADMIN");
        adminSafetyService.validateTeams(Collections.singleton(team.getId()));
        return team;
    }

    @Transactional
    public Team update(String id, UpdateTeamRequest request) {
        adminSafetyService.acquireWriteLock();
        permissionChecker.checkTeamAdmin(id);
        Team team = writableTeam(id);
        if (request == null) throw new BizException(40000, "请求不能为空");
        if (request.getIdentifier() != null && !request.getIdentifier().equals(team.getIdentifier()))
            throw new BizException(40904, "团队标识创建后不可修改");
        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) throw new BizException(40000, "团队名称不能为空");
            Team existing = teamRepository.findByName(request.getName());
            if (existing != null && !existing.getId().equals(id)) throw new BizException(40301, "团队名称已存在");
            team.setName(request.getName());
        }
        if (request.getColor() != null) team.setColor(request.getColor());
        if (request.getDescription() != null) team.setDescription(request.getDescription());
        teamRepository.update(team);
        return team;
    }

    /** Identifier reservation and resource preservation are intentional: archive instead of hard deletion. */
    public void delete(String id) {
        checkSuperAdmin();
        getById(id);
        throw new BizException(40903, "团队不可物理删除，请使用状态接口归档；归档不会停止Mock服务");
    }

    @Transactional
    public Team updateStatus(String id, UpdateTeamStatusRequest request) {
        adminSafetyService.acquireWriteLock();
        checkSuperAdmin();
        Team team = getById(id);
        String status = request == null ? null : request.getStatus();
        if (!("ACTIVE".equals(status) || "ARCHIVED".equals(status))) throw new BizException(40000, "未知团队状态");
        // Do not change resources, enabled flags or mock dispatch. Archive is an administrative write freeze.
        teamRepository.updateStatus(id, status); team.setStatus(status);
        return team;
    }

    public List<TeamMemberVO> listMembers(String teamId) {
        permissionChecker.checkTeamAccess(teamId); getById(teamId);
        return teamRepository.findMembers(teamId);
    }

    public List<MemberCandidateVO> memberCandidates(String teamId, String username) {
        permissionChecker.checkTeamAdmin(teamId); getById(teamId);
        if (username == null || username.trim().isEmpty()) throw new BizException(40000, "请输入完整用户名");
        User user = userRepository.findByUsername(username);
        if (user == null || !user.isActive() || userRepository.existsUserTeam(user.getId(), teamId)) return Collections.emptyList();
        return Collections.singletonList(new MemberCandidateVO(user.getId(), user.getUsername(), user.getDisplayName()));
    }

    @Transactional
    public void addMember(String teamId, AddMemberRequest request) {
        adminSafetyService.acquireWriteLock();
        permissionChecker.checkTeamAdmin(teamId); writableTeam(teamId);
        if (request == null) throw new BizException(40000, "请求不能为空");
        String role = RoleInputs.teamRole(request.getRole());
        requireActiveUser(request.getUserId());
        if (userRepository.existsUserTeam(request.getUserId(), teamId)) throw new BizException(40905, "用户已在团队中");
        teamRepository.addMember(teamId, request.getUserId(), role);
    }

    @Transactional
    public void removeMember(String teamId, String userId) {
        adminSafetyService.acquireWriteLock();
        permissionChecker.checkTeamAdmin(teamId); writableTeam(teamId);
        requireMember(teamId, userId);
        List<String> adminTeams = adminSafetyService.effectiveAdminTeams(userId);
        teamRepository.removeMember(teamId, userId);
        if (adminTeams.contains(teamId)) adminSafetyService.validateTeams(Collections.singleton(teamId));
    }

    @Transactional
    public void updateMemberRole(String teamId, String userId, UpdateMemberRoleRequest request) {
        adminSafetyService.acquireWriteLock();
        permissionChecker.checkTeamAdmin(teamId); writableTeam(teamId);
        requireMember(teamId, userId);
        String role = RoleInputs.teamRole(request == null ? null : request.getRole());
        List<String> adminTeams = adminSafetyService.effectiveAdminTeams(userId);
        if ("TEAM_ADMIN".equals(role)) requireActiveUser(userId);
        teamRepository.updateMemberRole(teamId, userId, role);
        if (adminTeams.contains(teamId)) adminSafetyService.validateTeams(Collections.singleton(teamId));
    }

    @Transactional
    public void leave(String teamId) {
        adminSafetyService.acquireWriteLock();
        permissionChecker.checkTeamAccess(teamId); writableTeam(teamId);
        String userId = SecurityContextUtil.getCurrentUserId();
        requireMember(teamId, userId); // A global super admin is not automatically a member.
        List<String> adminTeams = adminSafetyService.effectiveAdminTeams(userId);
        teamRepository.removeMember(teamId, userId);
        if (adminTeams.contains(teamId)) adminSafetyService.validateTeams(Collections.singleton(teamId));
    }

    private Team writableTeam(String teamId) {
        Team team = getById(teamId);
        if (team.isArchived()) throw new BizException(40906, "团队已归档，管理写入被冻结，请先由超级管理员恢复");
        return team;
    }
    private void requireMember(String teamId, String userId) {
        if (!userRepository.existsUserTeam(userId, teamId)) throw new BizException(40404, "团队成员不存在");
    }
    private void requireActiveUser(String userId) {
        User user = userRepository.findById(userId);
        if (user == null || !user.isActive()) throw new BizException(40000, "目标账号不存在或已停用");
    }
    private void checkSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) throw new BizException(40101, "无操作权限");
    }
}
