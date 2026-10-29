package com.mockhub.auth.repository;

import com.mockhub.auth.model.AuthSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuthSessionRepository {
    private final JdbcTemplate jdbc;

    public AuthSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Conditional insert serializes login against disable/password changes in SQLite's write transaction. */
    public boolean insertIfCurrent(AuthSession session) {
        return jdbc.update("INSERT INTO auth_session (id, user_id, auth_version, created_at, expires_at, user_agent, ip_address) "
                        + "SELECT ?, id, auth_version, ?, ?, ?, ? FROM user "
                        + "WHERE id = ? AND status = 'ACTIVE' AND auth_version = ?",
                session.getId(), session.getCreatedAt(), session.getExpiresAt(), session.getUserAgent(),
                session.getIpAddress(), session.getUserId(), session.getAuthVersion()) == 1;
    }

    public boolean isActive(String id, String userId, long authVersion, long now) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM auth_session s JOIN user u ON u.id = s.user_id "
                        + "WHERE s.id = ? AND s.user_id = ? AND s.auth_version = ? AND s.expires_at > ? "
                        + "AND s.revoked_at IS NULL AND u.status = 'ACTIVE' AND u.auth_version = s.auth_version",
                Integer.class, id, userId, authVersion, now);
        return count != null && count == 1;
    }

    public List<AuthSession> findActiveByUser(String userId, long now) {
        return jdbc.query("SELECT s.* FROM auth_session s JOIN user u ON u.id = s.user_id "
                        + "WHERE s.user_id = ? AND s.revoked_at IS NULL AND s.expires_at > ? "
                        + "AND u.status = 'ACTIVE' AND u.auth_version = s.auth_version ORDER BY s.created_at DESC, s.id",
                ROW_MAPPER, userId, now);
    }

    /** Ownership is part of the write predicate; callers never revoke another user's session. */
    public int revoke(String id, String userId, String now) {
        return jdbc.update("UPDATE auth_session SET revoked_at = ? WHERE id = ? AND user_id = ? AND revoked_at IS NULL",
                now, id, userId);
    }

    public int revokeAllByUser(String userId, String now) {
        return jdbc.update("UPDATE auth_session SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL", now, userId);
    }

    private static final RowMapper<AuthSession> ROW_MAPPER = (rs, rowNum) -> {
        AuthSession session = new AuthSession();
        session.setId(rs.getString("id"));
        session.setUserId(rs.getString("user_id"));
        session.setAuthVersion(rs.getLong("auth_version"));
        session.setCreatedAt(rs.getString("created_at"));
        session.setExpiresAt(rs.getLong("expires_at"));
        session.setRevokedAt(rs.getString("revoked_at"));
        session.setUserAgent(rs.getString("user_agent"));
        session.setIpAddress(rs.getString("ip_address"));
        return session;
    };
}
