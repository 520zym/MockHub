package com.mockhub.mcp.repository;

import com.mockhub.mcp.model.McpToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * MCP Token 数据访问。
 */
@Repository
public class McpTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public McpTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(McpToken token) {
        jdbcTemplate.update("INSERT INTO mcp_token "
                        + "(id, name, token_hash, token_prefix, user_id, created_at, last_used_at, expires_at, revoked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                token.getId(), token.getName(), token.getTokenHash(), token.getTokenPrefix(),
                token.getUserId(), token.getCreatedAt(), token.getLastUsedAt(),
                token.getExpiresAt(), token.getRevokedAt());
    }

    public McpToken findByHash(String hash) {
        List<McpToken> list = jdbcTemplate.query(
                "SELECT * FROM mcp_token WHERE token_hash = ?", ROW_MAPPER, hash);
        return list.isEmpty() ? null : list.get(0);
    }

    public McpToken findById(String id) {
        List<McpToken> list = jdbcTemplate.query(
                "SELECT * FROM mcp_token WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 列出 Token，带创建人显示名；超管可见全部。
     */
    public List<McpToken> findAllWithOwner() {
        String sql = "SELECT t.*, u.username, u.display_name FROM mcp_token t "
                + "LEFT JOIN user u ON u.id = t.user_id ORDER BY t.created_at DESC";
        return jdbcTemplate.query(sql, new RowMapper<McpToken>() {
            @Override
            public McpToken mapRow(ResultSet rs, int rowNum) throws SQLException {
                McpToken t = ROW_MAPPER.mapRow(rs, rowNum);
                String displayName = rs.getString("display_name");
                String username = rs.getString("username");
                t.setOwnerName(displayName != null && !displayName.isEmpty() ? displayName : username);
                return t;
            }
        });
    }

    public void revoke(String id, String now) {
        jdbcTemplate.update("UPDATE mcp_token SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
                now, id);
    }

    public void touchLastUsed(String id, String now) {
        jdbcTemplate.update("UPDATE mcp_token SET last_used_at = ? WHERE id = ?", now, id);
    }

    private static final RowMapper<McpToken> ROW_MAPPER = new RowMapper<McpToken>() {
        @Override
        public McpToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            McpToken t = new McpToken();
            t.setId(rs.getString("id"));
            t.setName(rs.getString("name"));
            t.setTokenHash(rs.getString("token_hash"));
            t.setTokenPrefix(rs.getString("token_prefix"));
            t.setUserId(rs.getString("user_id"));
            t.setCreatedAt(rs.getString("created_at"));
            t.setLastUsedAt(rs.getString("last_used_at"));
            t.setExpiresAt(rs.getString("expires_at"));
            t.setRevokedAt(rs.getString("revoked_at"));
            return t;
        }
    };
}
