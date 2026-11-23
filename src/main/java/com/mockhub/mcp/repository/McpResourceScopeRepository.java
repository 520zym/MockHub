package com.mockhub.mcp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

/** Authoritative target-team lookup; client-supplied teamId is never a resource ownership proof. */
@Repository
public class McpResourceScopeRepository {
    private final JdbcTemplate jdbc;
    public McpResourceScopeRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String apiTeam(String id){List<String> ids=jdbc.queryForList("SELECT team_id FROM api_definition WHERE id=? AND deleted_at IS NULL",String.class,id);return ids.isEmpty()?null:ids.get(0);}
    public String logTeam(String id){List<String> ids=jdbc.queryForList("SELECT team_id FROM request_log WHERE id=?",String.class,id);return ids.isEmpty()?null:ids.get(0);}
    public Map<String,Object> action(String id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT team_id,user_id,action_type FROM ai_pending_action WHERE id=?",id);return rows.isEmpty()?null:rows.get(0);}
}
