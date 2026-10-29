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
    private final int historyLimit;
    public ApiLifecycleRepository(JdbcTemplate jdbc) { this(jdbc,50); }
    public ApiLifecycleRepository(JdbcTemplate jdbc,int historyLimit) { this.jdbc=jdbc;this.historyLimit=Math.max(1,historyLimit); }

    public <T> T write(Supplier<T> work) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> {
            // First statement is a write, BEFORE reading permissions or expected versions. Never nest BEGIN IMMEDIATE.
            if(jdbc.update("UPDATE api_lifecycle_lock SET marker=marker WHERE id=1")!=1)
                throw new IllegalStateException("接口生命周期写锁缺失，拒绝写入");
            return work.get();
        });
    }

    public void append(String apiId,String action,String source,String restoredFrom) {
        requireTransaction();
        source=com.mockhub.mock.lifecycle.ApiMutationSource.effective(source);
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
        if(api.get("deleted_at")==null) prune(apiId);
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

    private void requireTransaction() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("API aggregate mutation requires a transaction");
    }

    private String scope(List<String> teams,String teamId,List<Object> args) {
        if(teamId!=null && !teamId.isEmpty()) { args.add(teamId);return " AND a.team_id=? "; }
        if(teams==null) return "";
        if(teams.isEmpty()) return " AND 1=0 ";
        args.addAll(teams);return " AND a.team_id IN ("+String.join(",",Collections.nCopies(teams.size(),"?"))+") ";
    }
    public List<Map<String,Object>> trash(List<String> teams,String teamId,int offset,int limit) {
        List<Object> args=new ArrayList<Object>();String scope=scope(teams,teamId,args);
        args.add(limit);args.add(offset);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.team_id,a.name,a.type,a.method,a.path,a.revision,a.deleted_at,a.deleted_by,a.purge_after,"
                + "COALESCE(u.display_name,u.username,'已删除账号') AS deleted_name,t.name AS team_name,t.identifier "
                + "FROM api_definition a LEFT JOIN user u ON u.id=a.deleted_by LEFT JOIN team t ON t.id=a.team_id "
                + "WHERE a.deleted_at IS NOT NULL"+scope+" ORDER BY a.deleted_at DESC,a.id LIMIT ? OFFSET ?",args.toArray());
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
        String[][] columns={{"id","id"},{"team_id","teamId"},{"name","name"},{"type","type"},{"method","method"},{"path","path"},
                {"revision","revision"},{"deleted_at","deletedAt"},{"deleted_by","deletedBy"},{"purge_after","purgeAfter"},
                {"deleted_name","deletedByName"},{"team_name","teamName"},{"identifier","teamIdentifier"}};
        for(Map<String,Object> row:rows) {
            Map<String,Object> item=new LinkedHashMap<String,Object>();
            for(String[] column:columns)item.put(column[1],row.get(column[0]));result.add(item);
        }
        return result;
    }
    public long countTrash(List<String> teams,String teamId) {
        List<Object> args=new ArrayList<Object>();String scope=scope(teams,teamId,args);
        return jdbc.queryForObject("SELECT COUNT(*) FROM api_definition a WHERE a.deleted_at IS NOT NULL"+scope,Long.class,args.toArray());
    }

    @SuppressWarnings("unchecked")
    public void applySnapshot(String apiId,Map<String,Object> snapshot) {
        requireTransaction();
        if(!(snapshot.get("schemaVersion") instanceof Number) || ((Number)snapshot.get("schemaVersion")).intValue()!=1)
            throw new BizException(40924,"SNAPSHOT_VERSION_UNSUPPORTED: 不能恢复此快照格式");
        Map<String,Object> config=(Map<String,Object>)snapshot.get("config");
        List<Object> args=new ArrayList<Object>();List<String> setters=new ArrayList<String>();
        for(String column:ApiSnapshot.CONFIG_COLUMNS) if(config.containsKey(column)) { setters.add(column+"=?");args.add(config.get(column)); }
        args.add(apiId);update("UPDATE api_definition SET "+String.join(",",setters)+" WHERE id=?",args);
        jdbc.update("DELETE FROM api_response WHERE api_id=?",apiId);
        List<String> allowed=Arrays.asList("id","api_id","soap_operation_name","name","response_code","content_type","response_body","body_type",
                "file_name","file_path","download_name","file_size","delay_ms","is_active","sort_order","conditions","created_at","updated_at");
        for(Map<String,Object> response:(List<Map<String,Object>>)snapshot.get("responses")) {
            List<String> columns=new ArrayList<String>();List<Object> values=new ArrayList<Object>();
            for(String column:allowed) if(response.containsKey(column)) {
                columns.add(column);values.add("api_id".equals(column)?apiId:response.get(column));
            }
            update("INSERT INTO api_response("+String.join(",",columns)+") VALUES("
                    +String.join(",",Collections.nCopies(columns.size(),"?"))+")",values);
        }
        jdbc.update("DELETE FROM api_tag WHERE api_id=?",apiId);
        for(Map<String,Object> tag:(List<Map<String,Object>>)snapshot.get("tags")) {
            jdbc.update("INSERT OR IGNORE INTO api_tag(api_id,tag_id) SELECT a.id,t.id FROM api_definition a JOIN tag t "
                    + "ON t.team_id=a.team_id WHERE a.id=? AND t.id=?",apiId,tag.get("id"));
        }
    }

    private void update(String sql,List<Object> values) {
        // SQLite JDBC cannot reliably infer null parameter metadata for a dynamically built statement.
        jdbc.update(sql,ps -> {
            for(int i=0;i<values.size();i++) {
                if(values.get(i)==null) ps.setNull(i+1,java.sql.Types.NULL);else ps.setObject(i+1,values.get(i));
            }
        });
    }
    private void withHistoryDeletion(Runnable work) {
        requireTransaction();jdbc.update("UPDATE api_lifecycle_lock SET history_delete_allowed=1 WHERE id=1");
        try { work.run(); } finally { jdbc.update("UPDATE api_lifecycle_lock SET history_delete_allowed=0 WHERE id=1"); }
    }
    private void prune(String apiId) {
        List<String> old=jdbc.queryForList("SELECT id FROM api_revision WHERE api_id=? ORDER BY revision DESC LIMIT -1 OFFSET ?",
                String.class,apiId,historyLimit);
        if(old.isEmpty()) return;
        withHistoryDeletion(() -> {
            for(String id:old) {
                jdbc.update("DELETE FROM mock_blob_ref WHERE owner_type='REVISION' AND owner_id=?",id);
                jdbc.update("DELETE FROM api_revision WHERE id=? AND api_id=?",id,apiId);
            }
        });
        jdbc.update("INSERT INTO operation_log(id,team_id,user_id,username,action,target_type,target_id,detail,created_at) "
                        + "SELECT ?,team_id,?,?,'REVISION_PRUNE','API',id,?,? FROM api_definition WHERE id=?",
                UUID.randomUUID().toString(),SecurityContextUtil.getCurrentUserId(),SecurityContextUtil.getCurrentUsername(),
                "pruned="+old.size()+", retention="+historyLimit,Instant.now().toString(),apiId);
    }

    public void purge(com.mockhub.mock.model.entity.ApiDefinition api,String actor) {
        requireTransaction();String id=api.getId(),now=Instant.now().toString();
        if(api.getDeletedAt()==null) throw new BizException(40926,"仅回收站中的接口可以彻底删除");
        jdbc.update("INSERT INTO api_tombstone(api_id,team_id,name,method,path,revision,deleted_at,deleted_by,purged_at,purged_by) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?)",id,api.getTeamId(),api.getName(),api.getMethod(),api.getPath(),api.getRevision(),
                api.getDeletedAt(),api.getDeletedBy(),now,actor);
        String actorName="系统清理";
        if(actor!=null) {
            List<String> names=jdbc.queryForList("SELECT COALESCE(NULLIF(display_name,''),username) FROM user WHERE id=?",String.class,actor);
            actorName=names.isEmpty()?actor:names.get(0);
        }
        jdbc.update("INSERT INTO operation_log(id,team_id,user_id,username,action,target_type,target_id,target_name,detail,created_at) "
                        + "VALUES(?,?,?,?,'PURGE','API',?,?,?,?)",UUID.randomUUID().toString(),api.getTeamId(),actor,actorName,id,api.getName(),
                "revision="+api.getRevision()+", method="+api.getMethod()+", path="+api.getPath(),now);
        jdbc.update("DELETE FROM mock_blob_ref WHERE (owner_type='API' AND owner_id=?) OR (owner_type='REVISION' AND owner_id IN "
                + "(SELECT id FROM api_revision WHERE api_id=?))",id,id);
        withHistoryDeletion(() -> jdbc.update("DELETE FROM api_revision WHERE api_id=?",id));
        jdbc.update("DELETE FROM mock_blob_ref WHERE owner_type='DRAFT' AND owner_id IN (SELECT id FROM ai_pending_action WHERE target_api_id=?)",id);
        jdbc.update("UPDATE ai_pending_action SET status='CANCELLED',payload='{}' WHERE target_api_id=?",id);
        jdbc.update("DELETE FROM api_tag WHERE api_id=?",id);jdbc.update("DELETE FROM api_response WHERE api_id=?",id);
        if(jdbc.update("DELETE FROM api_definition WHERE id=? AND revision=? AND deleted_at=?",id,api.getRevision(),api.getDeletedAt())!=1)
            throw com.mockhub.mock.lifecycle.ApiVersions.conflict();
    }
    public int purgeExpired(int limit) {
        return write(() -> {
            List<String> ids=jdbc.queryForList("SELECT id FROM api_definition WHERE deleted_at IS NOT NULL AND purge_after<=? "
                    + "ORDER BY purge_after LIMIT ?",String.class,Instant.now().toString(),limit);
            int purged=0;
            for(String id:ids) {
                com.mockhub.mock.model.entity.ApiDefinition api=new ApiRepository(jdbc).findIncludingDeleted(id);
                if(api!=null && api.getPurgeAfter()!=null && !Instant.parse(api.getPurgeAfter()).isAfter(Instant.now())) {
                    purge(api,null);purged++;
                }
            }
            return purged;
        });
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
