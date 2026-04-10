package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.enums.UserRole;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.model.dto.AddMemberRequest;
import com.mockhub.system.model.dto.CreateTeamRequest;
import com.mockhub.system.model.dto.TeamMemberVO;
import com.mockhub.system.model.dto.UpdateMemberRoleRequest;
import com.mockhub.system.model.dto.UpdateTeamRequest;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 团队服务实现
 * <p>
 * 负责团队 CRUD 业务逻辑，包括：
 * - 创建团队（name 和 identifier 唯一性检查）
 * - 修改团队信息
 * - 删除团队（有接口时不能删除）
 * - 团队成员管理（添加、移除、修改角色）
 * - 提供 Mock 分发和权限校验所需的查询方法
 */
@Service
public class TeamServiceImpl implements TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamServiceImpl.class);

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Team findByIdentifier(String identifier) {
        return teamRepository.findByIdentifier(identifier);
    }

    @Override
    public List<Team> findTeamsByUserId(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        // 查询用户的全局角色，超管返回全部团队
        User user = userRepository.findById(userId);
        if (user == null) {
            return Collections.emptyList();
        }

        List<Team> teams;
        if (UserRole.SUPER_ADMIN.name().equals(user.getGlobalRole())) {
            teams = teamRepository.findAll();
        } else {
            teams = teamRepository.findByUserId(userId);
        }

        // 填充非持久化字段
        for (Team team : teams) {
            team.setMemberCount(teamRepository.countMembersByTeamId(team.getId()));
            team.setApiCount(teamRepository.countApisByTeamId(team.getId()));
        }

        return teams;
    }

    @Override
    public Team getById(String teamId) {
        Team team = teamRepository.findById(teamId);
        if (team == null) {
            throw new BizException(40303, "团队不存在");
        }
        return team;
    }

    /**
     * 查询所有团队列表（含 memberCount 和 apiCount）
     * <p>
     * 仅超级管理员可调用。
     *
     * @return 团队列表
     */
    public List<Team> listAll() {
        checkSuperAdmin();
        log.info("查询所有团队列表");

        List<Team> teams = teamRepository.findAll();
        for (Team team : teams) {
            team.setMemberCount(teamRepository.countMembersByTeamId(team.getId()));
            team.setApiCount(teamRepository.countApisByTeamId(team.getId()));
        }
        return teams;
    }

    /**
     * 创建新团队
     *
     * @param request 创建团队请求
     * @return 创建后的团队对象
     * @throws BizException 40301 团队名称已存在
     * @throws BizException 40302 团队标识已存在
     */
    public Team create(CreateTeamRequest request) {
        checkSuperAdmin();

        // 检查名称唯一性
        Team existingByName = teamRepository.findByName(request.getName());
        if (existingByName != null) {
            throw new BizException(40301, "团队名称已存在");
        }

        // 检查标识唯一性
        Team existingByIdentifier = teamRepository.findByIdentifier(request.getIdentifier());
        if (existingByIdentifier != null) {
            throw new BizException(40302, "团队标识已存在");
        }

        Team team = new Team();
        team.setId(UUID.randomUUID().toString());
        team.setName(request.getName());
        team.setIdentifier(request.getIdentifier());
        team.setColor(request.getColor());
        team.setCreatedAt(LocalDateTime.now().toString());

        teamRepository.insert(team);
        log.info("创建团队成功：name={}, identifier={}", team.getName(), team.getIdentifier());
        return team;
    }

    /**
     * 修改团队信息
     *
     * @param id      团队 ID
     * @param request 修改请求（字段可选更新）
     * @return 修改后的团队对象
     * @throws BizException 40301 团队名称已存在
     * @throws BizException 40302 团队标识已存在
     */
    public Team update(String id, UpdateTeamRequest request) {
        checkSuperAdmin();

        Team team = teamRepository.findById(id);
        if (team == null) {
            throw new BizException(40303, "团队不存在");
        }

        // 如果修改了名称，检查唯一性
        if (request.getName() != null && !request.getName().equals(team.getName())) {
            Team existingByName = teamRepository.findByName(request.getName());
            if (existingByName != null) {
                throw new BizException(40301, "团队名称已存在");
            }
            team.setName(request.getName());
        }

        // 如果修改了标识，检查唯一性
        if (request.getIdentifier() != null && !request.getIdentifier().equals(team.getIdentifier())) {
            Team existingByIdentifier = teamRepository.findByIdentifier(request.getIdentifier());
            if (existingByIdentifier != null) {
                throw new BizException(40302, "团队标识已存在");
            }
            team.setIdentifier(request.getIdentifier());
        }

        // 更新颜色
        if (request.getColor() != null) {
            team.setColor(request.getColor());
        }

        teamRepository.update(team);
        log.info("修改团队成功：id={}, name={}", id, team.getName());
        return team;
    }

    /**
     * 删除团队
     *
     * @param id 团队 ID
     * @throws BizException 40303 团队下有接口，不能删除
     */
    public void delete(String id) {
        checkSuperAdmin();

        Team team = teamRepository.findById(id);
        if (team == null) {
            throw new BizException(40303, "团队不存在");
        }

        // 检查团队下是否有接口
        int apiCount = teamRepository.countApisByTeamId(id);
        if (apiCount > 0) {
            throw new BizException(40303, "团队下有接口，不能删除");
        }

        teamRepository.deleteById(id);
        log.info("删除团队成功：id={}, name={}", id, team.getName());
    }

    /**
     * 查询团队成员列表
     *
     * @param teamId 团队 ID
     * @return 成员列表
     */
    public List<TeamMemberVO> listMembers(String teamId) {
        checkSuperAdmin();
        log.info("查询团队成员列表：teamId={}", teamId);
        return teamRepository.findMembers(teamId);
    }

    /**
     * 添加团队成员
     *
     * @param teamId  团队 ID
     * @param request 添加成员请求
     * @throws BizException 40303 团队不存在
     */
    public void addMember(String teamId, AddMemberRequest request) {
        checkSuperAdmin();

        Team team = teamRepository.findById(teamId);
        if (team == null) {
            throw new BizException(40303, "团队不存在");
        }

        // 检查用户是否已在团队中
        boolean exists = userRepository.existsUserTeam(request.getUserId(), teamId);
        if (exists) {
            log.warn("用户已在团队中：userId={}, teamId={}", request.getUserId(), teamId);
            return;
        }

        teamRepository.addMember(teamId, request.getUserId(), request.getRole());
        log.info("添加团队成员成功：teamId={}, userId={}, role={}", teamId, request.getUserId(), request.getRole());
    }

    /**
     * 移除团队成员
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     */
    public void removeMember(String teamId, String userId) {
        checkSuperAdmin();
        teamRepository.removeMember(teamId, userId);
        log.info("移除团队成员成功：teamId={}, userId={}", teamId, userId);
    }

    /**
     * 修改团队成员角色
     *
     * @param teamId  团队 ID
     * @param userId  用户 ID
     * @param request 角色修改请求
     */
    public void updateMemberRole(String teamId, String userId, UpdateMemberRoleRequest request) {
        checkSuperAdmin();
        teamRepository.updateMemberRole(teamId, userId, request.getRole());
        log.info("修改团队成员角色成功：teamId={}, userId={}, role={}", teamId, userId, request.getRole());
    }

    /**
     * 校验当前用户是否为超级管理员
     */
    private void checkSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40101, "无操作权限");
        }
    }
}
