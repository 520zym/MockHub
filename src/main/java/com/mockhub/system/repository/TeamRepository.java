package com.mockhub.system.repository;

import com.mockhub.system.model.dto.TeamMemberVO;
import com.mockhub.system.model.entity.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 团队数据访问层
 * <p>
 * 操作 team 表、user_team 关联表，并支持统计 api_definition 数量。
 */
@Repository
public class TeamRepository {

    private static final Logger log = LoggerFactory.getLogger(TeamRepository.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** team 表行映射器 */
    private static final RowMapper<Team> TEAM_ROW_MAPPER = new RowMapper<Team>() {
        @Override
        public Team mapRow(ResultSet rs, int rowNum) throws SQLException {
            Team team = new Team();
            team.setId(rs.getString("id"));
            team.setName(rs.getString("name"));
            team.setIdentifier(rs.getString("identifier"));
            team.setColor(rs.getString("color"));
            team.setCreatedAt(rs.getString("created_at"));
            return team;
        }
    };

    /** 团队成员视图行映射器 */
    private static final RowMapper<TeamMemberVO> MEMBER_ROW_MAPPER = new RowMapper<TeamMemberVO>() {
        @Override
        public TeamMemberVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TeamMemberVO(
                    rs.getString("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("role")
            );
        }
    };

    /**
     * 根据 ID 查找团队
     *
     * @param id 团队 ID
     * @return 团队对象，未找到返回 null
     */
    public Team findById(String id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM team WHERE id = ?",
                    TEAM_ROW_MAPPER,
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 根据团队短标识查找团队（不区分大小写）
     *
     * @param identifier 团队短标识
     * @return 团队对象，未找到返回 null
     */
    public Team findByIdentifier(String identifier) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM team WHERE UPPER(identifier) = UPPER(?)",
                    TEAM_ROW_MAPPER,
                    identifier
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 根据团队名称查找团队
     *
     * @param name 团队名称
     * @return 团队对象，未找到返回 null
     */
    public Team findByName(String name) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM team WHERE name = ?",
                    TEAM_ROW_MAPPER,
                    name
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 查询所有团队
     *
     * @return 团队列表
     */
    public List<Team> findAll() {
        return jdbcTemplate.query("SELECT * FROM team ORDER BY created_at ASC", TEAM_ROW_MAPPER);
    }

    /**
     * 查询指定用户所属的团队列表
     *
     * @param userId 用户 ID
     * @return 团队列表
     */
    public List<Team> findByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT t.* FROM team t INNER JOIN user_team ut ON t.id = ut.team_id WHERE ut.user_id = ? ORDER BY t.created_at ASC",
                TEAM_ROW_MAPPER,
                userId
        );
    }

    /**
     * 插入新团队
     *
     * @param team 团队对象（id、createdAt 需预先设置）
     */
    public void insert(Team team) {
        log.debug("插入团队：name={}, identifier={}", team.getName(), team.getIdentifier());
        jdbcTemplate.update(
                "INSERT INTO team (id, name, identifier, color, created_at) VALUES (?, ?, ?, ?, ?)",
                team.getId(),
                team.getName(),
                team.getIdentifier(),
                team.getColor(),
                team.getCreatedAt()
        );
    }

    /**
     * 更新团队信息
     *
     * @param team 团队对象
     */
    public void update(Team team) {
        log.debug("更新团队：id={}, name={}", team.getId(), team.getName());
        jdbcTemplate.update(
                "UPDATE team SET name = ?, identifier = ?, color = ? WHERE id = ?",
                team.getName(),
                team.getIdentifier(),
                team.getColor(),
                team.getId()
        );
    }

    /**
     * 删除团队
     *
     * @param id 团队 ID
     */
    public void deleteById(String id) {
        log.debug("删除团队：id={}", id);
        jdbcTemplate.update("DELETE FROM team WHERE id = ?", id);
    }

    /**
     * 查询团队的成员列表
     *
     * @param teamId 团队 ID
     * @return 成员视图列表
     */
    public List<TeamMemberVO> findMembers(String teamId) {
        return jdbcTemplate.query(
                "SELECT u.id, u.username, u.display_name, ut.role FROM user u INNER JOIN user_team ut ON u.id = ut.user_id WHERE ut.team_id = ? ORDER BY ut.role ASC, u.username ASC",
                MEMBER_ROW_MAPPER,
                teamId
        );
    }

    /**
     * 添加团队成员
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @param role   团队内角色
     */
    public void addMember(String teamId, String userId, String role) {
        log.debug("添加团队成员：teamId={}, userId={}, role={}", teamId, userId, role);
        jdbcTemplate.update(
                "INSERT INTO user_team (user_id, team_id, role) VALUES (?, ?, ?)",
                userId,
                teamId,
                role
        );
    }

    /**
     * 移除团队成员
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     */
    public void removeMember(String teamId, String userId) {
        log.debug("移除团队成员：teamId={}, userId={}", teamId, userId);
        jdbcTemplate.update(
                "DELETE FROM user_team WHERE user_id = ? AND team_id = ?",
                userId,
                teamId
        );
    }

    /**
     * 更新团队成员角色
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @param role   新角色
     */
    public void updateMemberRole(String teamId, String userId, String role) {
        log.debug("更新团队成员角色：teamId={}, userId={}, role={}", teamId, userId, role);
        jdbcTemplate.update(
                "UPDATE user_team SET role = ? WHERE user_id = ? AND team_id = ?",
                role,
                userId,
                teamId
        );
    }

    /**
     * 统计团队下的接口数量
     *
     * @param teamId 团队 ID
     * @return 接口数量
     */
    public int countApisByTeamId(String teamId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE team_id = ?",
                Long.class,
                teamId
        );
        return count != null ? count.intValue() : 0;
    }

    /**
     * 统计团队的成员数量
     *
     * @param teamId 团队 ID
     * @return 成员数量
     */
    public int countMembersByTeamId(String teamId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_team WHERE team_id = ?",
                Long.class,
                teamId
        );
        return count != null ? count.intValue() : 0;
    }
}
