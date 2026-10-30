package com.mockhub.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.filter.JwtAuthFilter;
import com.mockhub.common.model.BizException;
import com.mockhub.mock.repository.WsdlReferenceRepository;
import com.mockhub.mock.service.SoapService;
import com.mockhub.mock.service.SoapSkeletonGenerator;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.common.util.CurrentUserProvider;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.mcp.repository.McpTokenRepository;
import com.mockhub.mcp.service.McpScopeCodec;
import com.mockhub.mcp.service.McpTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real historical SQLite schemas and real production startup, with every file confined to @TempDir. */
class LegacyDatabaseCompatibilityTest {
    @TempDir Path temp;
    private static final String LEGACY_MCP_TOKEN = "mhk_legacy-credential";
    private static final String BODY = "{\"legacy\":\"正文 & <tag>\"}";
    private static final String SOAP = "{\"wsdlFileName\":\"legacy.wsdl\",\"operations\":[{\"operationName\":\"Ping\",\"responseBody\":\"<reply>旧 SOAP</reply>\",\"responseCode\":202,\"delayMs\":17}]}";

    private static final String WSDL = "<?xml version='1.0' encoding='UTF-8'?>"
            + "<definitions xmlns='http://schemas.xmlsoap.org/wsdl/' xmlns:s='http://schemas.xmlsoap.org/wsdl/soap/' xmlns:tns='urn:legacy' targetNamespace='urn:legacy'>"
            + "<portType name='LegacyType'/><binding name='LegacyBinding' type='tns:LegacyType'><s:binding transport='http://schemas.xmlsoap.org/soap/http'/></binding>"
            + "<service name='Legacy'><port name='Port' binding='tns:LegacyBinding'><s:address location='http://real-service.example/legacy'/></port></service></definitions>";

    static Stream<LegacySchemaFixtures.Snapshot> histories() { return LegacySchemaFixtures.ALL.stream(); }

    @ParameterizedTest(name = "{0}")
    @MethodSource("histories")
    void knownHistoricalSchemaPreservesContentRelationshipsAndResourcesTwice(LegacySchemaFixtures.Snapshot history) throws Exception {
        SQLiteDataSource ds = legacyDatabase(history);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        assertHistoricalShape(ds, history);
        seed(ds, history);
        Path attachment = temp.resolve("files/a/legacy.bin");
        Files.createDirectories(attachment.getParent());
        byte[] bytes = new byte[]{0, 1, 42, -1};
        Files.write(attachment, bytes);
        Path wsdl = temp.resolve("wsdl/legacy.wsdl");
        Files.createDirectories(wsdl.getParent());
        Files.write(wsdl, WSDL.getBytes(StandardCharsets.UTF_8));
        List<String> originalDefinitions = rows(ds, "SELECT id,response_body,soap_config,created_at,updated_at FROM api_definition ORDER BY id");
        List<String> originalAccounts = rows(ds, "SELECT id,username,password_hash,display_name,first_login,created_at,updated_at FROM user ORDER BY id");
        List<String> originalTeams = rows(ds, "SELECT id,name,identifier,color,created_at FROM team ORDER BY id");
        List<String> originalRelations = rows(ds, "SELECT user_id,team_id FROM user_team ORDER BY user_id,team_id");
        List<String> originalResponses = hasTable(ds, "api_response")
                ? rows(ds, "SELECT id,api_id,soap_operation_name,response_body FROM api_response ORDER BY id") : Collections.emptyList();
        String oldRevision = history.version >= 10 ? jdbc.queryForObject("SELECT snapshot_json FROM api_revision WHERE api_id='rest'", String.class) : null;

        start(); // exact DataSource bean path: no test-run schema.sql prepass
        assertEquals(11, jdbc.queryForObject("SELECT MAX(version) FROM schema_version", Integer.class));
        assertEquals(11, jdbc.queryForObject("SELECT count(*) FROM schema_version", Integer.class));
        assertEquals(originalDefinitions, rows(ds, "SELECT id,response_body,soap_config,created_at,updated_at FROM api_definition ORDER BY id"));
        assertEquals(originalAccounts, rows(ds, "SELECT id,username,password_hash,display_name,first_login,created_at,updated_at FROM user ORDER BY id"));
        assertEquals(originalTeams, rows(ds, "SELECT id,name,identifier,color,created_at FROM team ORDER BY id"));
        assertEquals(originalRelations, rows(ds, "SELECT user_id,team_id FROM user_team ORDER BY user_id,team_id"));
        assertTrue(rows(ds, "SELECT id,api_id,soap_operation_name,response_body FROM api_response ORDER BY id").containsAll(originalResponses));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM api_response", Integer.class));
        assertEquals(history.version >= 3 ? 37 : 0, jdbc.queryForObject("SELECT hit_count FROM api_definition WHERE id='rest'", Integer.class));
        assertEquals(history.version >= 3 ? "last-called-original" : null, jdbc.queryForObject("SELECT last_called_at FROM api_definition WHERE id='rest'", String.class));
        assertEquals(history.version >= 5 ? "RANDOM" : "CONDITION", jdbc.queryForObject("SELECT response_mode FROM api_definition WHERE id='rest'", String.class));
        assertEquals(history.version == 0 && !hasOriginalResponses(history) ? BODY : "existing-response-body",
                jdbc.queryForObject("SELECT response_body FROM api_response WHERE api_id='rest'", String.class));
        assertEquals("<reply>旧 SOAP</reply>", jdbc.queryForObject("SELECT response_body FROM api_response WHERE api_id='soap'", String.class));
        assertEquals(202, jdbc.queryForObject("SELECT response_code FROM api_response WHERE api_id='soap'", Integer.class));
        assertEquals(17, jdbc.queryForObject("SELECT delay_ms FROM api_response WHERE api_id='soap'", Integer.class));
        assertEquals("SUPER_ADMIN", jdbc.queryForObject("SELECT global_role FROM user WHERE id='root'", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM user WHERE global_role='USER'", Integer.class));
        assertEquals("TEAM_ADMIN", jdbc.queryForObject("SELECT role FROM user_team WHERE user_id='root'", String.class));
        assertEquals("EDITOR", jdbc.queryForObject("SELECT role FROM user_team WHERE user_id='member' AND team_id='a'", String.class));
        assertEquals("VIEWER", jdbc.queryForObject("SELECT role FROM user_team WHERE user_id='viewer'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_team WHERE team_id='orphan' AND role='TEAM_ADMIN'", Integer.class));
        assertEquals(history.version >= 8 ? "DISABLED" : "ACTIVE", jdbc.queryForObject("SELECT status FROM user WHERE id='viewer'", String.class));
        assertEquals(history.version >= 8 ? 7 : 0, jdbc.queryForObject("SELECT auth_version FROM user WHERE id='viewer'", Integer.class));
        assertEquals(history.version >= 9 ? "ARCHIVED" : "ACTIVE", jdbc.queryForObject("SELECT status FROM team WHERE id='orphan'", String.class));
        assertTrue(jdbc.queryForObject("SELECT sql FROM sqlite_master WHERE name='idx_api_definition_team_path_method'", String.class).contains("WHERE deleted_at IS NULL"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sqlite_master WHERE name='idx_api_definition_last_called_at'", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM api_revision", Integer.class));
        if (history.version < 10) assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM api_revision WHERE action='MIGRATION_BASELINE' AND actor_id IS NULL", Integer.class));
        else assertEquals(oldRevision, jdbc.queryForObject("SELECT snapshot_json FROM api_revision WHERE api_id='rest'", String.class));
        if (history.version >= 4) {
            assertEquals("a/legacy.bin", jdbc.queryForObject("SELECT file_path FROM api_response WHERE api_id='file'", String.class));
            assertEquals("FILE", jdbc.queryForObject("SELECT body_type FROM api_response WHERE api_id='file'", String.class));
            assertEquals("legacy.bin", jdbc.queryForObject("SELECT file_name FROM api_response WHERE api_id='file'", String.class));
            assertEquals("下载.bin", jdbc.queryForObject("SELECT download_name FROM api_response WHERE api_id='file'", String.class));
            assertEquals(4, jdbc.queryForObject("SELECT file_size FROM api_response WHERE api_id='file'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM mock_blob_ref WHERE file_path='a/legacy.bin'", Integer.class));
        }
        if (hasTable(ds, "file_server_file") && history.version >= 5) {
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM file_server_file WHERE file_id='stored' AND download_count=9 AND deleted=0", Integer.class));
        }
        if (hasLegacyAi(history)) {
            assertEquals(McpTokenService.sha256(LEGACY_MCP_TOKEN), jdbc.queryForObject("SELECT token_hash FROM mcp_token WHERE id='legacy-token'", String.class));
            assertNull(new McpTokenService(new McpTokenRepository(jdbc)).authenticate(LEGACY_MCP_TOKEN));
            assertNotNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='legacy-token'", String.class));
            assertEquals("LEGACY", jdbc.queryForObject("SELECT source_type FROM ai_pending_action WHERE id='old-draft'", String.class));
            assertNull(jdbc.queryForObject("SELECT source_token_id FROM ai_pending_action WHERE id='old-draft'", String.class));
            assertEquals("{\"apiId\":\"rest\"}", jdbc.queryForObject("SELECT payload FROM ai_pending_action WHERE id='old-draft'", String.class));
        }
        if (history.name.equals("v5-ai-pre-vendor")) assertEquals("deepseek", jdbc.queryForObject("SELECT vendor FROM ai_settings WHERE id='default'", String.class));
        assertEquals(history.version >= 8 ? 1 : 0, jdbc.queryForObject("SELECT count(*) FROM auth_session", Integer.class));
        if (history.version >= 8) assertNull(jdbc.queryForObject("SELECT revoked_at FROM auth_session WHERE id='existing-session'", String.class));
        if (history.version >= 11) assertTrue(McpScopeCodec.isUsable(new McpTokenRepository(jdbc).findById("scoped-token")));
        assertLegacyJwtRejected();
        List<String> first = dump(ds);
        start();
        assertEquals(first, dump(ds), "second startup must preserve all rows, bodies, IDs, roles, revocations and schema");
        assertLegacyJwtRejected();
        if (hasLegacyAi(history)) assertNull(new McpTokenService(new McpTokenRepository(jdbc)).authenticate(LEGACY_MCP_TOKEN));
        assertArrayEquals(bytes, Files.readAllBytes(attachment));
        assertEquals(WSDL, new String(Files.readAllBytes(wsdl), StandardCharsets.UTF_8));
        // Retained bytes alone are insufficient: old basename references must still work at runtime.
        DataProperties props = new DataProperties(); props.setPath(temp.toString());
        SoapService soap = new SoapService(props, mock(SoapSkeletonGenerator.class));
        TeamRepository teams = new TeamRepository(); ReflectionTestUtils.setField(teams, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(soap, "teamRepository", teams);
        ReflectionTestUtils.setField(soap, "references", new WsdlReferenceRepository(jdbc, new ObjectMapper()));
        String mockUrl = "http://127.0.0.1/mock/LEGACY/soap";
        String served = soap.getWsdlContent("a", "legacy.wsdl", mockUrl);
        assertTrue(served.contains(mockUrl));
        assertFalse(served.contains("http://real-service.example/legacy"));
        assertEquals(40702, assertThrows(BizException.class,
                () -> soap.getWsdlContent("orphan", "legacy.wsdl", mockUrl)).getCode());
        assertArrayEquals(WSDL.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(wsdl));
    }

    @Test void freshDatabaseUsesSameAtomicStartupPathTwice() throws Exception {
        start();
        SQLiteDataSource ds = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        assertEquals(11, jdbc.queryForObject("SELECT count(*) FROM schema_version", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM api_definition", Integer.class));
        List<String> first = dump(ds); start(); assertEquals(first, dump(ds));
    }

    SQLiteDataSource legacyDatabase(LegacySchemaFixtures.Snapshot history) throws Exception {
        SQLiteDataSource ds = dataSource();
        try (Connection conn = ds.getConnection()) { history.create(conn); }
        return ds;
    }

    private SQLiteDataSource dataSource() {
        SQLiteDataSource ds = new SQLiteDataSource(); ds.setUrl("jdbc:sqlite:" + temp.resolve("legacy.db")); return ds;
    }

    private void start() { start(temp); }

    static void start(Path temp) {
        DataProperties path = new DataProperties(); path.setPath(temp.toString());
        DataSourceConfig config = new DataSourceConfig(); ReflectionTestUtils.setField(config, "dataProperties", path);
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:sqlite:" + temp.resolve("legacy.db")); properties.setDriverClassName("org.sqlite.JDBC");
        properties.setType(SQLiteDataSource.class); config.dataSource(properties);
    }

    private static boolean hasOriginalResponses(LegacySchemaFixtures.Snapshot h) { return !h.name.equals("unversioned-initial"); }
    private static boolean hasLegacyAi(LegacySchemaFixtures.Snapshot h) { return h.version >= 6 || h.name.equals("v5-ai-pre-vendor"); }

    private void assertHistoricalShape(DataSource ds, LegacySchemaFixtures.Snapshot h) throws Exception {
        assertEquals(h.version > 0, hasTable(ds, "schema_version"));
        assertEquals(hasOriginalResponses(h), hasTable(ds, "api_response"));
        assertEquals(hasOriginalResponses(h), hasColumn(ds, "api_definition", "description"));
        assertEquals(h.version >= 3, hasColumn(ds, "api_definition", "hit_count"));
        assertEquals(h.version >= 4, hasColumn(ds, "api_response", "file_path"));
        assertEquals(h.version >= 5, hasColumn(ds, "api_definition", "response_mode"));
        assertEquals(h.version >= 6, hasColumn(ds, "ai_settings", "vendor"));
        assertEquals(h.version >= 7, hasColumn(ds, "mcp_settings", "tool_update_api_draft"));
        assertEquals(h.version >= 8, hasColumn(ds, "user", "auth_version"));
        assertEquals(h.version >= 9, hasColumn(ds, "team", "status"));
        assertEquals(h.version >= 10, hasColumn(ds, "api_definition", "revision"));
        assertEquals(h.version >= 11, hasColumn(ds, "mcp_token", "scope_version"));
    }

    private void seed(DataSource ds, LegacySchemaFixtures.Snapshot h) throws Exception {
        JdbcTemplate j = new JdbcTemplate(ds);
        String[] ids = {"root", "member", "team-admin", "viewer"};
        String[] roles = {"SUPER_ADMIN", "MEMBER", "TEAM_ADMIN", "USER"};
        String[] teamRoles = {"TEAM_ADMIN", "MEMBER", "EDITOR", "VIEWER"};
        for (int i = 0; i < ids.length; i++) j.update("INSERT INTO user(id,username,password_hash,display_name,global_role,first_login,created_at,updated_at) VALUES(?,?,?, ?,?,0,'created','updated')", ids[i], ids[i], "password-hash-secret", ids[i], roles[i]);
        j.update("INSERT INTO team(id,name,identifier,created_at) VALUES('a','Alpha','AA','team-created'),('orphan','Orphan','OO','team-created')");
        for (int i = 0; i < ids.length; i++) j.update("INSERT INTO user_team VALUES(?,'a',?)", ids[i], teamRoles[i]);
        j.update("INSERT INTO user_team VALUES('member','orphan','MEMBER')");
        if (h.version >= 8) {
            j.update("UPDATE user SET status='DISABLED',auth_version=7 WHERE id='viewer'");
            j.update("INSERT INTO auth_session(id,user_id,auth_version,created_at,expires_at) VALUES('existing-session','member',0,'session-created',4102444800000)");
        }
        if (h.version >= 9) j.update("UPDATE team SET status='ARCHIVED' WHERE id='orphan'");
        for (String id : new String[]{"rest", "soap", "file"}) {
            j.update("INSERT INTO api_definition(id,team_id,type,name,method,path,response_code,response_body,soap_config,created_at,updated_at) VALUES(?,'a',?,?,'POST',?,201,?,?,'api-created','api-updated')", id, id.equals("soap") ? "SOAP" : "REST", "Old " + id, "/" + id, BODY, id.equals("soap") ? SOAP : null);
        }
        if (h.version >= 3) j.update("UPDATE api_definition SET hit_count=37,last_called_at='last-called-original' WHERE id='rest'");
        if (h.version >= 5) j.update("UPDATE api_definition SET response_mode='RANDOM' WHERE id='rest'");
        if (hasOriginalResponses(h)) {
            for (String id : new String[]{"rest", "soap", "file"}) j.update("INSERT INTO api_response(id,api_id,soap_operation_name,name,response_code,content_type,response_body,delay_ms,is_active,sort_order,created_at,updated_at) VALUES(?,?,?,'Original',?,'text/xml',?,?,1,4,'response-created','response-updated')", "response-" + id, id, id.equals("soap") ? "Ping" : null, id.equals("soap") ? 202 : 203, id.equals("soap") ? "<reply>旧 SOAP</reply>" : "existing-response-body", id.equals("soap") ? 17 : 33);
        }
        if (h.version >= 4) j.update("UPDATE api_response SET body_type='FILE',file_path='a/legacy.bin',file_name='legacy.bin',download_name='下载.bin',file_size=4 WHERE api_id='file'");
        if (h.version >= 5) j.update("INSERT INTO file_server_file(file_id,team_id,file_name,content_type,file_size,uploaded_at,download_count) VALUES('stored','a','server.bin','application/octet-stream',4,'uploaded',9)");
        if (hasLegacyAi(h)) {
            j.update("UPDATE ai_settings SET base_url='https://api.deepseek.com/v1',api_key='api-key-secret' WHERE id='default'");
            j.update("INSERT INTO mcp_token(id,name,token_hash,token_prefix,user_id,created_at) VALUES('legacy-token','Old',?,'prefix','member','token-created')", McpTokenService.sha256(LEGACY_MCP_TOKEN));
            j.update("INSERT INTO ai_pending_action(id,user_id,team_id,action_type,payload,created_at,expires_at" + (h.version >= 11 ? ",source_type" : "") + ") VALUES('old-draft','member','a','UPDATE_API','{\"apiId\":\"rest\"}','draft-created','2099-01-01'" + (h.version >= 11 ? ",'LEGACY'" : "") + ")");
        }
        if (h.version >= 11) j.update("INSERT INTO mcp_token(id,name,token_hash,token_prefix,user_id,created_at,expires_at,scope_version,team_ids,scopes) VALUES('scoped-token','Scoped','scoped-hash','prefix','member','token-created','2099-01-01T00:00:00Z',1,'[\"a\"]','{\"a\":[\"API_READ\"]}')");
        if (h.version >= 10) {
            for (String id : new String[]{"rest", "soap", "file"}) j.update("INSERT INTO api_revision(id,api_id,team_id,revision,action,actor_name_snapshot,created_at,source,snapshot_json) VALUES(?,?,'a',1,'CREATE','Historical actor','revision-created','WEB',?)", "revision-" + id, id, "{\"schemaVersion\":1,\"config\":{\"name\":\"Historical " + id + "\"},\"responses\":[]}");
            j.update("INSERT INTO mock_blob(file_path,team_id) VALUES('a/legacy.bin','a')");
            j.update("INSERT INTO mock_blob_ref(file_path,owner_type,owner_id) VALUES('a/legacy.bin','API','file'),('a/legacy.bin','REVISION','revision-file')");
        }
    }

    private void assertLegacyJwtRejected() throws Exception {
        JwtUtil jwt = new JwtUtil(); jwt.init();
        String token = jwt.generateToken("member", "member", "MEMBER", Collections.singletonList("a"));
        JwtAuthFilter filter = new JwtAuthFilter(jwt, new ObjectMapper(), mock(CurrentUserProvider.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean accepted = new AtomicBoolean();
        filter.doFilter(request, response, (r, s) -> accepted.set(true));
        assertFalse(accepted.get()); assertEquals(401, response.getStatus());
    }

    static void migrate(DataSource ds) throws Exception {
        Method migration = DataSourceConfig.class.getDeclaredMethod("executeMigrations", DataSource.class);
        migration.setAccessible(true);
        try { migration.invoke(new DataSourceConfig(), ds); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        }
    }

    static boolean hasTable(DataSource ds, String table) throws Exception {
        return new JdbcTemplate(ds).queryForObject("SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?", Integer.class, table) > 0;
    }
    static boolean hasColumn(DataSource ds, String table, String column) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equals(rs.getString("name"))) return true;
            return false;
        }
    }
    static List<String> rows(DataSource ds, String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) row.add(rs.getObject(i));
                values.add(new ObjectMapper().writeValueAsString(row));
            }
        }
        Collections.sort(values); return values;
    }
    static List<String> dump(DataSource ds) throws Exception {
        List<String> result = new ArrayList<>(rows(ds, "SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name"));
        List<String> tables = new JdbcTemplate(ds).queryForList("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", String.class);
        for (String table : tables) {
            result.add("TABLE " + table);
            result.addAll(rows(ds, "SELECT * FROM \"" + table.replace("\"", "\"\"") + "\""));
        }
        return result;
    }
}
