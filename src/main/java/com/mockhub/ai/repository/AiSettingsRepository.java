package com.mockhub.ai.repository;

import com.mockhub.ai.model.AiSettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * AI 配置数据访问。表 ai_settings 只有 id='default' 一行。
 */
@Repository
public class AiSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 读取配置；表为空时返回 null，由 Service 层给出默认值。
     */
    public AiSettings load() {
        List<AiSettings> list = jdbcTemplate.query(
                "SELECT * FROM ai_settings WHERE id = ?", ROW_MAPPER, AiSettings.DEFAULT_ID);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 覆盖保存（UPSERT）。SQLite 下用 INSERT OR REPLACE 保证单行语义。
     */
    public void save(AiSettings s) {
        String sql = "INSERT OR REPLACE INTO ai_settings (id, enabled, provider, vendor, base_url, model, api_key, "
                + "cap_response_body, cap_conditions, cap_explain, cap_drafts, access_scope, updated_at, updated_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                s.getId(),
                s.isEnabled() ? 1 : 0,
                s.getProvider(),
                s.getVendor(),
                s.getBaseUrl(),
                s.getModel(),
                s.getApiKey(),
                s.isCapResponseBody() ? 1 : 0,
                s.isCapConditions() ? 1 : 0,
                s.isCapExplain() ? 1 : 0,
                s.isCapDrafts() ? 1 : 0,
                s.getAccessScope(),
                s.getUpdatedAt(),
                s.getUpdatedBy());
    }

    private static final RowMapper<AiSettings> ROW_MAPPER = new RowMapper<AiSettings>() {
        @Override
        public AiSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiSettings s = new AiSettings();
            s.setId(rs.getString("id"));
            s.setEnabled(rs.getInt("enabled") == 1);
            s.setProvider(rs.getString("provider"));
            s.setVendor(rs.getString("vendor"));
            s.setBaseUrl(rs.getString("base_url"));
            s.setModel(rs.getString("model"));
            s.setApiKey(rs.getString("api_key"));
            s.setCapResponseBody(rs.getInt("cap_response_body") == 1);
            s.setCapConditions(rs.getInt("cap_conditions") == 1);
            s.setCapExplain(rs.getInt("cap_explain") == 1);
            s.setCapDrafts(rs.getInt("cap_drafts") == 1);
            s.setAccessScope(rs.getString("access_scope"));
            s.setUpdatedAt(rs.getString("updated_at"));
            s.setUpdatedBy(rs.getString("updated_by"));
            return s;
        }
    };
}