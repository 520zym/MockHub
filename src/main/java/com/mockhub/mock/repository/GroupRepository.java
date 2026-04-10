package com.mockhub.mock.repository;

import com.mockhub.mock.model.entity.ApiGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 接口分组数据访问层
 * <p>
 * 使用 JdbcTemplate 操作 api_group 表。
 */
@Repository
public class GroupRepository {

    private static final Logger log = LoggerFactory.getLogger(GroupRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<ApiGroup> ROW_MAPPER = new RowMapper<ApiGroup>() {
        @Override
        public ApiGroup mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiGroup group = new ApiGroup();
            group.setId(rs.getString("id"));
            group.setTeamId(rs.getString("team_id"));
            group.setName(rs.getString("name"));
            group.setSortOrder(rs.getInt("sort_order"));
            group.setCreatedAt(rs.getString("created_at"));
            return group;
        }
    };

    public GroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据 ID 查询分组
     */
    public ApiGroup findById(String id) {
        List<ApiGroup> list = jdbcTemplate.query(
                "SELECT * FROM api_group WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询团队下所有分组（按 sortOrder 升序）
     */
    public List<ApiGroup> findByTeamId(String teamId) {
        return jdbcTemplate.query(
                "SELECT * FROM api_group WHERE team_id = ? ORDER BY sort_order ASC, created_at ASC",
                ROW_MAPPER, teamId);
    }

    /**
     * 插入分组
     */
    public void insert(ApiGroup group) {
        jdbcTemplate.update(
                "INSERT INTO api_group (id, team_id, name, sort_order, created_at) VALUES (?, ?, ?, ?, ?)",
                group.getId(), group.getTeamId(), group.getName(), group.getSortOrder(), group.getCreatedAt());
    }

    /**
     * 更新分组
     */
    public void update(ApiGroup group) {
        jdbcTemplate.update(
                "UPDATE api_group SET name = ?, sort_order = ? WHERE id = ?",
                group.getName(), group.getSortOrder(), group.getId());
    }

    /**
     * 删除分组
     *
     * @param id 分组 ID
     * @return 受影响行数
     */
    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM api_group WHERE id = ?", id);
    }
}
