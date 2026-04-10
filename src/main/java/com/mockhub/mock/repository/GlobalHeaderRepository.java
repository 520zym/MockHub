package com.mockhub.mock.repository;

import com.mockhub.mock.model.entity.GlobalHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 全局响应头数据访问层
 * <p>
 * 使用 JdbcTemplate 操作 global_header 表。
 */
@Repository
public class GlobalHeaderRepository {

    private static final Logger log = LoggerFactory.getLogger(GlobalHeaderRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<GlobalHeader> ROW_MAPPER = new RowMapper<GlobalHeader>() {
        @Override
        public GlobalHeader mapRow(ResultSet rs, int rowNum) throws SQLException {
            GlobalHeader header = new GlobalHeader();
            header.setId(rs.getString("id"));
            header.setTeamId(rs.getString("team_id"));
            header.setHeaderName(rs.getString("header_name"));
            header.setHeaderValue(rs.getString("header_value"));
            header.setEnabled(rs.getInt("enabled") == 1);
            header.setSortOrder(rs.getInt("sort_order"));
            return header;
        }
    };

    public GlobalHeaderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询团队的所有全局响应头（按 sortOrder 升序）
     */
    public List<GlobalHeader> findByTeamId(String teamId) {
        return jdbcTemplate.query(
                "SELECT * FROM global_header WHERE team_id = ? ORDER BY sort_order ASC",
                ROW_MAPPER, teamId);
    }

    /**
     * 查询团队已启用的全局响应头（按 sortOrder 升序，Mock 分发时使用）
     */
    public List<GlobalHeader> findEnabledByTeamId(String teamId) {
        return jdbcTemplate.query(
                "SELECT * FROM global_header WHERE team_id = ? AND enabled = 1 ORDER BY sort_order ASC",
                ROW_MAPPER, teamId);
    }

    /**
     * 整体替换团队的全局响应头（先删后插）
     *
     * @param teamId  团队 ID
     * @param headers 新的全局响应头列表
     */
    public void replaceAll(String teamId, List<GlobalHeader> headers) {
        // 先删除旧数据
        jdbcTemplate.update("DELETE FROM global_header WHERE team_id = ?", teamId);

        // 再批量插入新数据
        if (headers != null && !headers.isEmpty()) {
            for (GlobalHeader header : headers) {
                jdbcTemplate.update(
                        "INSERT INTO global_header (id, team_id, header_name, header_value, enabled, sort_order) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                        header.getId(), teamId, header.getHeaderName(), header.getHeaderValue(),
                        header.isEnabled() ? 1 : 0, header.getSortOrder());
            }
        }
    }
}
