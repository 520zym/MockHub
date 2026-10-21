package com.mockhub.ai.repository;

import com.mockhub.ai.model.AiPendingAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * AI 待确认操作数据访问。
 */
@Repository
public class AiPendingActionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiPendingActionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(AiPendingAction action) {
        jdbcTemplate.update("INSERT INTO ai_pending_action "
                        + "(id, user_id, team_id, action_type, payload, summary, status, created_at, expires_at, confirmed_at, result_api_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                action.getId(), action.getUserId(), action.getTeamId(), action.getActionType(),
                action.getPayload(), action.getSummary(), action.getStatus(), action.getCreatedAt(),
                action.getExpiresAt(), action.getConfirmedAt(), action.getResultApiId());
    }

    public AiPendingAction findById(String id) {
        List<AiPendingAction> list = jdbcTemplate.query(
                "SELECT * FROM ai_pending_action WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 抢占执行权：只有 PENDING 才能从 CONFIRMING 状态开始执行，避免重复确认导致重复创建。
     *
     * @return 受影响行数，1 表示抢占成功
     */
    public int claim(String id, String now) {
        return jdbcTemplate.update(
                "UPDATE ai_pending_action SET status = 'CONFIRMING', confirmed_at = ? WHERE id = ? AND status = 'PENDING'",
                now, id);
    }

    public void complete(String id, String apiId, String now) {
        jdbcTemplate.update(
                "UPDATE ai_pending_action SET status = 'CONFIRMED', confirmed_at = ?, result_api_id = ? WHERE id = ?",
                now, apiId, id);
    }

    public void release(String id) {
        jdbcTemplate.update(
                "UPDATE ai_pending_action SET status = 'PENDING', confirmed_at = NULL WHERE id = ? AND status = 'CONFIRMING'",
                id);
    }

    public void cancel(String id, String now) {
        jdbcTemplate.update(
                "UPDATE ai_pending_action SET status = 'CANCELLED', confirmed_at = ? WHERE id = ? AND status = 'PENDING'",
                now, id);
    }

    public void expire(String id, String now) {
        jdbcTemplate.update(
                "UPDATE ai_pending_action SET status = 'EXPIRED', confirmed_at = ? WHERE id = ? AND status = 'PENDING'",
                now, id);
    }

    public int expireStale(String now) {
        return jdbcTemplate.update(
                "UPDATE ai_pending_action SET status = 'EXPIRED', confirmed_at = ? "
                        + "WHERE status = 'PENDING' AND expires_at < ?", now, now);
    }

    private static final RowMapper<AiPendingAction> ROW_MAPPER = new RowMapper<AiPendingAction>() {
        @Override
        public AiPendingAction mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiPendingAction action = new AiPendingAction();
            action.setId(rs.getString("id"));
            action.setUserId(rs.getString("user_id"));
            action.setTeamId(rs.getString("team_id"));
            action.setActionType(rs.getString("action_type"));
            action.setPayload(rs.getString("payload"));
            action.setSummary(rs.getString("summary"));
            action.setStatus(rs.getString("status"));
            action.setCreatedAt(rs.getString("created_at"));
            action.setExpiresAt(rs.getString("expires_at"));
            action.setConfirmedAt(rs.getString("confirmed_at"));
            action.setResultApiId(rs.getString("result_api_id"));
            return action;
        }
    };
}
