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

    public <T> T write(java.util.function.Supplier<T> work) {
        return new com.mockhub.mock.repository.ApiLifecycleRepository(jdbcTemplate).write(work);
    }

    public void insert(AiPendingAction action) {
        jdbcTemplate.update("INSERT INTO ai_pending_action "
                        + "(id, user_id, team_id, action_type, payload, summary, status, created_at, expires_at, confirmed_at, result_api_id, base_revision,target_api_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                action.getId(), action.getUserId(), action.getTeamId(), action.getActionType(),
                action.getPayload(), action.getSummary(), action.getStatus(), action.getCreatedAt(),
                action.getExpiresAt(), action.getConfirmedAt(), action.getResultApiId(),action.getBaseRevision(),action.getTargetApiId());
        protectDraftFiles(action);
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
        if(jdbcTemplate.update("UPDATE ai_pending_action SET status='CONFIRMED',confirmed_at=?,result_api_id=? WHERE id=? AND status='CONFIRMING'",
                now,apiId,id)!=1) throw new com.mockhub.common.model.BizException(40902,"草稿完成状态已改变");
        releaseFiles(id);
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
        releaseFiles(id);
    }

    public void expire(String id, String now) {
        jdbcTemplate.update(
                "UPDATE ai_pending_action SET status = 'EXPIRED', confirmed_at = ? WHERE id = ? AND status = 'PENDING'",
                now, id);
        releaseFiles(id);
    }

    public int expireStale(String now) {
        return write(() -> {
            int expired=jdbcTemplate.update("UPDATE ai_pending_action SET status='EXPIRED',confirmed_at=? WHERE status='PENDING' AND expires_at<=?",now,now);
            jdbcTemplate.update("DELETE FROM mock_blob_ref WHERE owner_type='DRAFT' AND owner_id IN "
                    + "(SELECT id FROM ai_pending_action WHERE status IN ('EXPIRED','CANCELLED','CONFIRMED'))");
            return expired;
        });
    }

    private void releaseFiles(String id) { jdbcTemplate.update("DELETE FROM mock_blob_ref WHERE owner_type='DRAFT' AND owner_id=?",id); }
    public void audit(AiPendingAction action,String apiId,String event) {
        jdbcTemplate.update("INSERT INTO operation_log(id,team_id,user_id,username,action,target_type,target_id,detail,created_at) "
                        + "VALUES(?,?,?,?,?,'AI_ACTION',?,?,?)",java.util.UUID.randomUUID().toString(),action.getTeamId(),
                com.mockhub.common.util.SecurityContextUtil.getCurrentUserId(),com.mockhub.common.util.SecurityContextUtil.getCurrentUsername(),
                event,action.getId(),"apiId="+apiId+", type="+action.getActionType(),java.time.Instant.now().toString());
    }
    private void protectDraftFiles(AiPendingAction action) {
        try {
            com.fasterxml.jackson.databind.JsonNode payload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(action.getPayload());
            java.util.Set<String> files=new java.util.HashSet<String>();collectFiles(payload,files);
            com.mockhub.mock.repository.MockBlobRepository blobs=new com.mockhub.mock.repository.MockBlobRepository(jdbcTemplate);
            for(String path:files) {
                blobs.requireUsable(action.getTeamId(),path);
                jdbcTemplate.update("INSERT OR IGNORE INTO mock_blob_ref(file_path,owner_type,owner_id,expires_at) VALUES(?,'DRAFT',?,?)",
                        path,action.getId(),java.time.Instant.parse(action.getExpiresAt()).toEpochMilli());
            }
        } catch(com.mockhub.common.model.BizException e) { throw e; }
        catch(Exception e) { throw new IllegalStateException("无法记录草稿附件引用",e); }
    }
    private void collectFiles(com.fasterxml.jackson.databind.JsonNode node,java.util.Set<String> files) {
        if(node.isObject() && "FILE".equalsIgnoreCase(node.path("bodyType").asText()) && node.hasNonNull("filePath")) files.add(node.path("filePath").asText());
        if(node.isContainerNode()) for(com.fasterxml.jackson.databind.JsonNode child:node)collectFiles(child,files);
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
            long base=rs.getLong("base_revision");action.setBaseRevision(rs.wasNull()?null:base);
            action.setTargetApiId(rs.getString("target_api_id"));
            return action;
        }
    };
}
