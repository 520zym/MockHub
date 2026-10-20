package com.mockhub.mcp.repository;

import com.mockhub.mcp.model.McpSettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * MCP 配置数据访问（单行）。
 */
@Repository
public class McpSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public McpSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public McpSettings load() {
        List<McpSettings> list = jdbcTemplate.query(
                "SELECT * FROM mcp_settings WHERE id = ?", ROW_MAPPER, McpSettings.DEFAULT_ID);
        return list.isEmpty() ? null : list.get(0);
    }

    public void save(McpSettings s) {
        String sql = "INSERT OR REPLACE INTO mcp_settings (id, enabled, write_mode, "
                + "tool_search_apis, tool_get_api_detail, tool_get_api_stats, tool_create_api_draft, "
                + "tool_apply_api_draft, tool_generate_response_body, tool_generate_conditions, "
                + "tool_explain_match_result, updated_at, updated_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                s.getId(),
                s.isEnabled() ? 1 : 0,
                s.getWriteMode(),
                s.isToolSearchApis() ? 1 : 0,
                s.isToolGetApiDetail() ? 1 : 0,
                s.isToolGetApiStats() ? 1 : 0,
                s.isToolCreateApiDraft() ? 1 : 0,
                s.isToolApplyApiDraft() ? 1 : 0,
                s.isToolGenerateResponseBody() ? 1 : 0,
                s.isToolGenerateConditions() ? 1 : 0,
                s.isToolExplainMatchResult() ? 1 : 0,
                s.getUpdatedAt(),
                s.getUpdatedBy());
    }

    private static final RowMapper<McpSettings> ROW_MAPPER = new RowMapper<McpSettings>() {
        @Override
        public McpSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
            McpSettings s = new McpSettings();
            s.setId(rs.getString("id"));
            s.setEnabled(rs.getInt("enabled") == 1);
            s.setWriteMode(rs.getString("write_mode"));
            s.setToolSearchApis(rs.getInt("tool_search_apis") == 1);
            s.setToolGetApiDetail(rs.getInt("tool_get_api_detail") == 1);
            s.setToolGetApiStats(rs.getInt("tool_get_api_stats") == 1);
            s.setToolCreateApiDraft(rs.getInt("tool_create_api_draft") == 1);
            s.setToolApplyApiDraft(rs.getInt("tool_apply_api_draft") == 1);
            s.setToolGenerateResponseBody(rs.getInt("tool_generate_response_body") == 1);
            s.setToolGenerateConditions(rs.getInt("tool_generate_conditions") == 1);
            s.setToolExplainMatchResult(rs.getInt("tool_explain_match_result") == 1);
            s.setUpdatedAt(rs.getString("updated_at"));
            s.setUpdatedBy(rs.getString("updated_by"));
            return s;
        }
    };
}
