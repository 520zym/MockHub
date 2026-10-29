package com.mockhub.mock.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;

/** Versioned, restorable configuration only: never authorization, identity, deletion or runtime counters. */
public final class ApiSnapshot {
    public static final List<String> CONFIG_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            "group_id", "type", "name", "description", "method", "path", "response_code", "content_type",
            "response_body", "delay_ms", "enabled", "global_header_overrides", "soap_config", "scenarios", "response_mode"));
    private ApiSnapshot() { }

    public static String capture(Connection conn, String apiId) throws SQLException {
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("schemaVersion", 1);
        Map<String, Object> api = query(conn, "SELECT * FROM api_definition WHERE id = ?", apiId).get(0);
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        for (String column : CONFIG_COLUMNS) if (api.containsKey(column)) config.put(column, api.get(column));
        snapshot.put("config", config);
        Object groupId=config.get("group_id");
        snapshot.put("group",groupId!=null && tableExists(conn,"api_group")
                ? query(conn,"SELECT id,name,sort_order FROM api_group WHERE id=?",groupId):Collections.emptyList());
        snapshot.put("responses", query(conn, "SELECT * FROM api_response WHERE api_id = ? ORDER BY sort_order, id", apiId));
        snapshot.put("tags", tableExists(conn, "api_tag") && tableExists(conn, "tag")
                ? query(conn, "SELECT t.id, t.name, t.color FROM api_tag a JOIN tag t ON t.id=a.tag_id WHERE a.api_id = ? ORDER BY t.id", apiId)
                : Collections.emptyList());
        try { return new ObjectMapper().writeValueAsString(snapshot); }
        catch (Exception e) { throw new SQLException("Cannot serialize API aggregate", e); }
    }

    @SuppressWarnings("unchecked")
    public static void protectBlobs(Connection conn,String teamId,String apiId,String revisionId,String snapshotJson) throws SQLException {
        try {
            Map<String,Object> snapshot=new ObjectMapper().readValue(snapshotJson,Map.class);
            try(PreparedStatement clear=conn.prepareStatement("DELETE FROM mock_blob_ref WHERE owner_type='API' AND owner_id=?")) {
                clear.setString(1,apiId); clear.executeUpdate();
            }
            for(Map<String,Object> response:(List<Map<String,Object>>)snapshot.get("responses")) {
                Object path=response.get("file_path");
                if(!"FILE".equalsIgnoreCase(String.valueOf(response.get("body_type"))) || path==null || path.toString().isEmpty()) continue;
                try(PreparedStatement blob=conn.prepareStatement("INSERT OR IGNORE INTO mock_blob(file_path,team_id) VALUES(?,?)")) {
                    blob.setString(1,path.toString()); blob.setString(2,teamId); blob.executeUpdate();
                }
                if(!path.toString().startsWith(teamId+"/")
                        || query(conn,"SELECT team_id FROM mock_blob WHERE file_path=? AND team_id=?",path.toString(),teamId).isEmpty())
                    throw new SQLException("Cross-team attachment reference: "+path);
                for(String[] owner:new String[][]{{"API",apiId},{"REVISION",revisionId}}) {
                    try(PreparedStatement ref=conn.prepareStatement("INSERT OR IGNORE INTO mock_blob_ref(file_path,owner_type,owner_id) VALUES(?,?,?)")) {
                        ref.setString(1,path.toString());ref.setString(2,owner[0]);ref.setString(3,owner[1]);ref.executeUpdate();
                    }
                }
            }
        } catch(SQLException e) { throw e; }
        catch(Exception e) { throw new SQLException("Cannot protect version attachment references",e); }
    }

    public static List<Map<String,Object>> query(Connection conn, String sql, Object... args) throws SQLException {
        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i=0; i<args.length; i++) ps.setObject(i+1,args[i]);
            try (ResultSet rs=ps.executeQuery()) {
                ResultSetMetaData metadata = rs.getMetaData();
                while (rs.next()) {
                    Map<String,Object> row=new LinkedHashMap<String,Object>();
                    for (int i=1;i<=metadata.getColumnCount();i++) row.put(metadata.getColumnLabel(i),rs.getObject(i));
                    result.add(row);
                }
            }
        }
        return result;
    }
    private static boolean tableExists(Connection conn,String table) throws SQLException {
        return !query(conn,"SELECT name FROM sqlite_master WHERE type='table' AND name=?",table).isEmpty();
    }
}
