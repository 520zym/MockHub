package com.mockhub.system.repository;

import com.mockhub.system.model.entity.User;
import com.mockhub.system.model.entity.UserTeam;
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
 * 用户数据访问层
 * <p>
 * 操作 user 表和 user_team 关联表，使用 JdbcTemplate 实现。
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** user 表行映射器 */
    private static final RowMapper<User> USER_ROW_MAPPER = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getString("id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setDisplayName(rs.getString("display_name"));
            user.setGlobalRole(rs.getString("global_role"));
            user.setFirstLogin(rs.getInt("first_login") == 1);
            user.setCreatedAt(rs.getString("created_at"));
            user.setUpdatedAt(rs.getString("updated_at"));
            return user;
        }
    };

    /** user_team 表行映射器 */
    private static final RowMapper<UserTeam> USER_TEAM_ROW_MAPPER = new RowMapper<UserTeam>() {
        @Override
        public UserTeam mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserTeam ut = new UserTeam();
            ut.setUserId(rs.getString("user_id"));
            ut.setTeamId(rs.getString("team_id"));
            ut.setRole(rs.getString("role"));
            return ut;
        }
    };

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户对象，未找到返回 null
     */
    public User findByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM user WHERE username = ?",
                    USER_ROW_MAPPER,
                    username
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 根据 ID 查找用户
     *
     * @param id 用户 ID
     * @return 用户对象，未找到返回 null
     */
    public User findById(String id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM user WHERE id = ?",
                    USER_ROW_MAPPER,
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    public List<User> findAll() {
        return jdbcTemplate.query("SELECT * FROM user ORDER BY created_at ASC", USER_ROW_MAPPER);
    }

    /**
     * 插入新用户
     *
     * @param user 用户对象（id、createdAt、updatedAt 需预先设置）
     */
    public void insert(User user) {
        log.debug("插入用户：username={}", user.getUsername());
        jdbcTemplate.update(
                "INSERT INTO user (id, username, password_hash, display_name, global_role, first_login, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                user.getGlobalRole(),
                user.isFirstLogin() ? 1 : 0,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    /**
     * 更新用户基本信息（displayName、globalRole、updatedAt）
     *
     * @param user 用户对象
     */
    public void update(User user) {
        log.debug("更新用户：id={}, username={}", user.getId(), user.getUsername());
        jdbcTemplate.update(
                "UPDATE user SET display_name = ?, global_role = ?, updated_at = ? WHERE id = ?",
                user.getDisplayName(),
                user.getGlobalRole(),
                user.getUpdatedAt(),
                user.getId()
        );
    }

    /**
     * 更新用户密码
     *
     * @param userId          用户 ID
     * @param newPasswordHash 新的密码哈希
     * @param updatedAt       更新时间
     */
    public void updatePassword(String userId, String newPasswordHash, String updatedAt) {
        log.debug("更新用户密码：userId={}", userId);
        jdbcTemplate.update(
                "UPDATE user SET password_hash = ?, updated_at = ? WHERE id = ?",
                newPasswordHash,
                updatedAt,
                userId
        );
    }

    /**
     * 更新首次登录标志
     *
     * @param userId     用户 ID
     * @param firstLogin 新的首次登录标志
     * @param updatedAt  更新时间
     */
    public void updateFirstLogin(String userId, boolean firstLogin, String updatedAt) {
        log.debug("更新首次登录标志：userId={}, firstLogin={}", userId, firstLogin);
        jdbcTemplate.update(
                "UPDATE user SET first_login = ?, updated_at = ? WHERE id = ?",
                firstLogin ? 1 : 0,
                updatedAt,
                userId
        );
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     */
    public void deleteById(String id) {
        log.debug("删除用户：id={}", id);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", id);
    }

    /**
     * 查询用户的团队角色列表
     *
     * @param userId 用户 ID
     * @return 团队角色关联列表
     */
    public List<UserTeam> findTeamRolesByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM user_team WHERE user_id = ?",
                USER_TEAM_ROW_MAPPER,
                userId
        );
    }

    /**
     * 替换用户的团队角色分配（先删除旧关联，再插入新关联）
     *
     * @param userId    用户 ID
     * @param teamRoles 新的团队角色列表
     */
    public void replaceTeamRoles(String userId, List<UserTeam> teamRoles) {
        log.debug("替换用户团队角色：userId={}, teamCount={}", userId, teamRoles.size());
        jdbcTemplate.update("DELETE FROM user_team WHERE user_id = ?", userId);
        for (UserTeam ut : teamRoles) {
            jdbcTemplate.update(
                    "INSERT INTO user_team (user_id, team_id, role) VALUES (?, ?, ?)",
                    ut.getUserId(),
                    ut.getTeamId(),
                    ut.getRole()
            );
        }
    }

    /**
     * 统计 user 表总记录数
     *
     * @return 用户总数
     */
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Long.class);
        return count != null ? count : 0;
    }

    /**
     * 检查用户是否属于指定团队
     *
     * @param userId 用户 ID
     * @param teamId 团队 ID
     * @return 存在关联返回 true
     */
    public boolean existsUserTeam(String userId, String teamId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_team WHERE user_id = ? AND team_id = ?",
                Long.class,
                userId,
                teamId
        );
        return count != null && count > 0;
    }

    /**
     * 查询用户在指定团队中的角色
     *
     * @param userId 用户 ID
     * @param teamId 团队 ID
     * @return 角色字符串，不存在返回 null
     */
    public String findUserTeamRole(String userId, String teamId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT role FROM user_team WHERE user_id = ? AND team_id = ?",
                    String.class,
                    userId,
                    teamId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
