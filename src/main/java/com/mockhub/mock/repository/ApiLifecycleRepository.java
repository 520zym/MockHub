package com.mockhub.mock.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mock.lifecycle.ApiSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Single SQLite write-transaction boundary for every aggregate mutation, including nested AI/import calls. */
public class ApiLifecycleRepository {
    private final JdbcTemplate jdbc;
    public ApiLifecycleRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public <T> T write(Supplier<T> work) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> {
            // First statement is a write, BEFORE reading permissions or expected versions. Never nest BEGIN IMMEDIATE.
            if(jdbc.update("UPDATE api_lifecycle_lock SET marker=marker WHERE id=1")!=1)
                throw new IllegalStateException("接口生命周期写锁缺失，拒绝写入");
            return work.get();
        });
    }

    public void append(String apiId,String action,String source,String restoredFrom) {
        String actor=SecurityContextUtil.getCurrentUserId();
        List<Map<String,Object>> names=jdbc.queryForList("SELECT username,display_name FROM user WHERE id=?",actor);
        String name=SecurityContextUtil.getCurrentUsername();
        if (!names.isEmpty()) {
            Object display=names.get(0).get("display_name");
            if (display!=null && !display.toString().trim().isEmpty()) name=display.toString();
        }
        String snapshot=jdbc.execute((ConnectionCallback<String>) conn -> ApiSnapshot.capture(conn,apiId));
        Map<String,Object> api=jdbc.queryForMap("SELECT team_id,revision,name,deleted_at FROM api_definition WHERE id=?",apiId);
        String id=UUID.randomUUID().toString(),now=Instant.now().toString();
        jdbc.update("INSERT INTO api_revision(id,api_id,team_id,revision,action,actor_id,actor_name_snapshot,created_at,source,snapshot_json,restored_from_revision_id) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,apiId,api.get("team_id"),api.get("revision"),action,actor,name,now,source,snapshot,restoredFrom);
        jdbc.execute((ConnectionCallback<Void>) conn -> {
            ApiSnapshot.protectBlobs(conn,api.get("team_id").toString(),apiId,id,snapshot); return null;
        });
        jdbc.update("INSERT INTO operation_log(id,team_id,user_id,username,action,target_type,target_id,target_name,detail,created_at) "
                        + "VALUES(?,?,?,?,?,'API',?,?,?,?)",UUID.randomUUID().toString(),api.get("team_id"),actor,name,
                action,apiId,api.get("name"),"revision="+api.get("revision")+", source="+source,now);
    }

    public List<Map<String,Object>> history(String apiId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,api_id,team_id,revision,action,actor_id,actor_name_snapshot,created_at,source,snapshot_schema_version,restored_from_revision_id "
                + "FROM api_revision WHERE api_id=? ORDER BY revision DESC",apiId);
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:rows) result.add(view(row));
        return result;
    }

    public Map<String,Object> revision(String apiId,String revisionId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM api_revision WHERE api_id=? AND id=?",apiId,revisionId);
        if(rows.isEmpty()) throw new BizException(40402,"版本不存在");
        Map<String,Object> result=view(rows.get(0));
        try { result.put("snapshot",new ObjectMapper().readValue(rows.get(0).get("snapshot_json").toString(),Map.class)); }
        catch(Exception e) { throw new IllegalStateException("版本快照损坏",e); }
        return result;
    }

    private Map<String,Object> view(Map<String,Object> row) {
        Map<String,Object> view=new LinkedHashMap<String,Object>();
        String[][] columns={{"id","id"},{"api_id","apiId"},{"team_id","teamId"},{"revision","revision"},
                {"action","action"},{"actor_id","actorId"},{"actor_name_snapshot","actorNameSnapshot"},
                {"created_at","createdAt"},{"source","source"},{"snapshot_schema_version","snapshotSchemaVersion"},
                {"restored_from_revision_id","restoredFromRevisionId"}};
        for(String[] column:columns) view.put(column[1],row.get(column[0]));
        return view;
    }
}
