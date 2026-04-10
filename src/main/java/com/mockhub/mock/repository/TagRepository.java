package com.mockhub.mock.repository;

import com.mockhub.mock.model.entity.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 标签数据访问层
 * <p>
 * 使用 JdbcTemplate 操作 tag 表。
 */
@Repository
public class TagRepository {

    private static final Logger log = LoggerFactory.getLogger(TagRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Tag> ROW_MAPPER = new RowMapper<Tag>() {
        @Override
        public Tag mapRow(ResultSet rs, int rowNum) throws SQLException {
            Tag tag = new Tag();
            tag.setId(rs.getString("id"));
            tag.setTeamId(rs.getString("team_id"));
            tag.setName(rs.getString("name"));
            tag.setColor(rs.getString("color"));
            return tag;
        }
    };

    public TagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据 ID 查询标签
     */
    public Tag findById(String id) {
        List<Tag> list = jdbcTemplate.query("SELECT * FROM tag WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询团队下所有标签
     */
    public List<Tag> findByTeamId(String teamId) {
        return jdbcTemplate.query(
                "SELECT * FROM tag WHERE team_id = ? ORDER BY name ASC",
                ROW_MAPPER, teamId);
    }

    /**
     * 根据 ID 列表批量查询标签
     */
    public List<Tag> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM tag WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sql.append(i > 0 ? ",?" : "?");
        }
        sql.append(")");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, ids.toArray());
    }

    /**
     * 插入标签
     */
    public void insert(Tag tag) {
        jdbcTemplate.update(
                "INSERT INTO tag (id, team_id, name, color) VALUES (?, ?, ?, ?)",
                tag.getId(), tag.getTeamId(), tag.getName(), tag.getColor());
    }

    /**
     * 更新标签
     */
    public void update(Tag tag) {
        jdbcTemplate.update(
                "UPDATE tag SET name = ?, color = ? WHERE id = ?",
                tag.getName(), tag.getColor(), tag.getId());
    }

    /**
     * 删除标签
     *
     * @param id 标签 ID
     * @return 受影响行数
     */
    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM tag WHERE id = ?", id);
    }
}
