package com.mockhub.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * schema_version 版本化迁移单元测试（v1.4.4 引入）。
 *
 * <p>覆盖 DataSourceConfig#executeMigrations 在四类启动场景下的正确性：
 * <ul>
 *   <li>case1：全新库——schema.sql 已建好所有表（含 description 列与 api_response 表），
 *       无任何业务数据。迁移应幂等跳过、写入 schema_version=2（v1+v2 都已应用）。</li>
 *   <li>case2：v1.4.3 发版老库——api_definition 缺 description 列，api_response 表虽存在
 *       但为空（由 schema.sql 的 IF NOT EXISTS 建出）。迁移应真实执行建列 + REST/SOAP
 *       数据迁入，并写入 schema_version=1。</li>
 *   <li>case3：已跑过 soap-mock-enhancement 的开发者本地库——列已在、数据已迁入。
 *       迁移应幂等跳过，不重复插入数据。</li>
 *   <li>case4：幂等二次启动——同一个库连续跑两次迁移，第二次应不改变数据且不新增
 *       schema_version 记录。</li>
 * </ul>
 *
 * <p>通过反射调用私有方法 executeMigrations(DataSource)，避免为测试开放生产代码可见性。
 */
class SchemaVersionMigrationTest {

    @TempDir
    Path tempDir;

    private DataSource dataSource;
    private DataSourceConfig config;
    private Method executeMigrations;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sds = new SQLiteDataSource();
        sds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toString());
        this.dataSource = sds;
        execSql("CREATE TABLE user (id TEXT PRIMARY KEY, username TEXT)",
                "INSERT INTO user VALUES ('legacy', 'legacy')");

        this.config = new DataSourceConfig();
        this.executeMigrations = DataSourceConfig.class
                .getDeclaredMethod("executeMigrations", DataSource.class);
        this.executeMigrations.setAccessible(true);
    }

    /**
     * 通过反射调用 DataSourceConfig#executeMigrations(DataSource)
     */
    private void runMigration() throws Exception {
        executeMigrations.invoke(config, dataSource);
    }

    /**
     * 批量执行 DDL/DML，用于 setup 场景
     */
    private void execSql(String... statements) throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    /**
     * 读取 schema_version 表中已记录的最大版本号
     */
    private int readSchemaVersion() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * 读取 schema_version 表总行数，用于幂等验证
     */
    private int countSchemaVersionRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 读取 ai_settings.vendor，验证旧配置推断。 */
    private String readAiVendor() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT vendor FROM ai_settings WHERE id = 'default'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /**
     * 读取 api_response 总行数
     */
    private int countApiResponse() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM api_response")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * 通过 PRAGMA table_info 判断列是否存在
     */
    private boolean columnExists(String table, String column) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 模拟 schema.sql 已完整执行过的新库状态（含 description 列、含 schema_version 表）
     */
    private void setupFullSchema() throws Exception {
        execSql(
                "CREATE TABLE api_definition (id TEXT PRIMARY KEY, team_id TEXT DEFAULT 'legacy', name TEXT DEFAULT 'Legacy', method TEXT, path TEXT, type TEXT, " +
                        "response_code INTEGER, content_type TEXT, response_body TEXT, " +
                        "delay_ms INTEGER, soap_config TEXT, created_at TEXT, updated_at TEXT, " +
                        "description TEXT)",
                "CREATE TABLE api_response (id TEXT PRIMARY KEY, api_id TEXT, " +
                        "soap_operation_name TEXT, name TEXT, response_code INTEGER, " +
                        "content_type TEXT, response_body TEXT, delay_ms INTEGER, " +
                        "is_active INTEGER, sort_order INTEGER, created_at TEXT, updated_at TEXT)",
                "CREATE TABLE schema_version (version INTEGER PRIMARY KEY, " +
                        "applied_at TEXT DEFAULT (datetime('now')), description TEXT)",
                "CREATE TABLE ai_settings (id TEXT PRIMARY KEY, enabled INTEGER, provider TEXT, " +
                        "base_url TEXT, model TEXT, api_key TEXT)"
        );
    }

    /**
     * 模拟 v1.4.3 发版老库：api_definition 缺 description 列，但 api_response 和
     * schema_version 已由 schema.sql 的 IF NOT EXISTS 建出（空表）。
     * 并预置 1 条 REST + 1 条 SOAP（operation=login）的历史数据。
     */
    private void setupV143LegacyDbWithData() throws Exception {
        execSql(
                // 老 api_definition（不含 description 列）
                "CREATE TABLE api_definition (id TEXT PRIMARY KEY, team_id TEXT DEFAULT 'legacy', name TEXT DEFAULT 'Legacy', method TEXT, path TEXT, type TEXT, " +
                        "response_code INTEGER, content_type TEXT, response_body TEXT, " +
                        "delay_ms INTEGER, soap_config TEXT, created_at TEXT, updated_at TEXT)",
                // api_response 由 schema.sql 建出
                "CREATE TABLE api_response (id TEXT PRIMARY KEY, api_id TEXT, " +
                        "soap_operation_name TEXT, name TEXT, response_code INTEGER, " +
                        "content_type TEXT, response_body TEXT, delay_ms INTEGER, " +
                        "is_active INTEGER, sort_order INTEGER, created_at TEXT, updated_at TEXT)",
                // schema_version 也由 schema.sql 建出
                "CREATE TABLE schema_version (version INTEGER PRIMARY KEY, " +
                        "applied_at TEXT DEFAULT (datetime('now')), description TEXT)",
                "CREATE TABLE ai_settings (id TEXT PRIMARY KEY, enabled INTEGER, provider TEXT, " +
                        "base_url TEXT, model TEXT, api_key TEXT)",
                // 1 条 REST 接口
                "INSERT INTO api_definition(id, type, response_code, content_type, " +
                        "response_body, delay_ms, created_at, updated_at) VALUES (" +
                        "'api-rest-1', 'REST', 200, 'application/json', '{\"ok\":true}', 50, " +
                        "'2026-01-01', '2026-01-01')",
                // 1 条 SOAP 接口，内含 1 个 operation
                "INSERT INTO api_definition(id, type, soap_config, created_at, updated_at) " +
                        "VALUES ('api-soap-1', 'SOAP', " +
                        "'{\"operations\":[{\"operationName\":\"login\",\"responseBody\":\"<response/>\"," +
                        "\"responseCode\":200,\"delayMs\":0}]}', '2026-01-01', '2026-01-01')"
        );
    }

    // ---------- case1：全新库 ----------

    @Test
    void case1_freshDb_recordsVersion1AndKeepsEmpty() throws Exception {
        setupFullSchema();

        runMigration();

        assertEquals(11, readSchemaVersion(), "全新库应记录 schema_version=10（v1 至 v10）");
        assertTrue(columnExists("api_definition", "description"),
                "description 列应已存在（全新库 schema.sql 已建出）");
        assertTrue(columnExists("api_definition", "group_id"),
                "group_id 列应已被 v2 迁移幂等添加");
        assertTrue(columnExists("api_definition", "hit_count"),
                "hit_count 列应已被 v3 迁移幂等添加");
        assertTrue(columnExists("api_definition", "last_called_at"),
                "last_called_at 列应已被 v3 迁移幂等添加");
        assertTrue(columnExists("api_response", "body_type"),
                "body_type 列应已被 v4 迁移幂等添加");
        assertTrue(columnExists("api_definition", "response_mode"),
                "response_mode 列应已被 v5 迁移幂等添加");
        assertEquals(0, countApiResponse(), "全新库无数据，api_response 应为空");
    }

    @Test
    void case1b_v6InfersVendorFromExistingBaseUrl() throws Exception {
        setupFullSchema();
        execSql("INSERT INTO ai_settings(id, enabled, provider, base_url, model, api_key) " +
                "VALUES('default', 1, 'OPENAI_COMPATIBLE', 'https://api.deepseek.com/v1', 'deepseek-chat', 'enc:v1:x')");

        runMigration();

        assertEquals("deepseek", readAiVendor(), "旧配置应按 Base URL 推断为 DeepSeek");
    }

    @Test
    void case1c_v7MissingColumns_areRepairedWithoutVersionChange() throws Exception {
        setupFullSchema();
        execSql("INSERT INTO schema_version(version, description) VALUES(7, 'legacy v7')");

        runMigration();

        assertEquals(11, readSchemaVersion(), "兼容补列不应改变 schema_version");
        assertTrue(columnExists("ai_settings", "vendor"), "旧 v7 库缺 vendor 时应自动补列");
        assertTrue(columnExists("api_response", "conditions"), "旧库缺 conditions 时应自动补列");
        assertTrue(columnExists("api_definition", "scenarios"), "旧库缺 scenarios 时应自动补列");
    }

    @Test
    void fullSchemaPassBeforeMigrationDoesNotRequireNewUserColumnsOnLegacyDatabase() throws Exception {
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Method schema = DataSourceConfig.class.getDeclaredMethod("executeSchemaSql", org.springframework.jdbc.core.JdbcTemplate.class);
        schema.setAccessible(true);
        schema.invoke(config, jdbc); // old user has no status/auth_version yet
        execSql("INSERT INTO schema_version(version,description) VALUES(7,'old v7')");
        runMigration();
        schema.invoke(config, jdbc);
        assertEquals(11, readSchemaVersion());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM user WHERE id='legacy'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT auth_version FROM user WHERE id='legacy'", Integer.class));
        assertTrue(columnExists("auth_session", "expires_at"));
        runMigration();
        assertEquals(5, countSchemaVersionRows());
    }

    @Test
    void v11LegacyTokensAreRevokedAndPendingSourceCannotBeInventedOnUpgrade() throws Exception {
        setupFullSchema();
        execSql("CREATE TABLE mcp_token(id TEXT PRIMARY KEY, revoked_at TEXT)",
                "INSERT INTO mcp_token VALUES('old',NULL)",
                "CREATE TABLE ai_pending_action(id TEXT PRIMARY KEY)",
                "INSERT INTO ai_pending_action VALUES('unbound')");
        runMigration();
        try(Connection conn=dataSource.getConnection();Statement st=conn.createStatement()){
            try(ResultSet rs=st.executeQuery("SELECT scope_version,team_ids,scopes,revoked_at FROM mcp_token WHERE id='old'")){
                assertTrue(rs.next());assertEquals(0,rs.getInt(1));org.junit.jupiter.api.Assertions.assertNull(rs.getString(2));org.junit.jupiter.api.Assertions.assertNull(rs.getString(3));org.junit.jupiter.api.Assertions.assertNotNull(rs.getString(4));
            }
            try(ResultSet rs=st.executeQuery("SELECT source_type,source_token_id,source_scope_json FROM ai_pending_action WHERE id='unbound'")){
                assertTrue(rs.next());assertEquals("LEGACY",rs.getString(1));org.junit.jupiter.api.Assertions.assertNull(rs.getString(2));org.junit.jupiter.api.Assertions.assertNull(rs.getString(3));
            }
            st.execute("UPDATE mcp_token SET scope_version=1,team_ids='[\"a\"]',scopes='{\"a\":[\"API_READ\"]}',revoked_at=NULL WHERE id='old'");
        }
        runMigration();
        try(Connection conn=dataSource.getConnection();Statement st=conn.createStatement();ResultSet rs=st.executeQuery("SELECT revoked_at FROM mcp_token WHERE id='old'")){
            assertTrue(rs.next());org.junit.jupiter.api.Assertions.assertNull(rs.getString(1));
        }
    }

    @Test
    void v8PreservesDisabledAccountsAndVersionsAcrossRepeatedUpgrade() throws Exception {
        setupFullSchema();
        runMigration();
        assertTrue(columnExists("user", "status"));
        assertTrue(columnExists("user", "auth_version"));
        execSql("UPDATE user SET status='DISABLED', auth_version=7 WHERE id='legacy'");
        runMigration();
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT status, auth_version FROM user WHERE id='legacy'")) {
            assertTrue(rs.next());
            assertEquals("DISABLED", rs.getString(1));
            assertEquals(7, rs.getLong(2));
        }
    }

    @Test
    void v8FailureRollsBackAndPreventsStartup() throws Exception {
        setupFullSchema();
        execSql("CREATE VIEW auth_session AS SELECT id FROM user");
        java.lang.reflect.InvocationTargetException failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.lang.reflect.InvocationTargetException.class, this::runMigration);
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertEquals(0, countSchemaVersionRows());
        org.junit.jupiter.api.Assertions.assertFalse(columnExists("user", "status"));
    }

    @Test
    void v9MapsLegacyRolesWithoutLosingRelationshipsOrPromotingOrphans() throws Exception {
        setupFullSchema();
        execSql("ALTER TABLE user ADD COLUMN global_role TEXT DEFAULT 'MEMBER'",
                "CREATE TABLE team(id TEXT PRIMARY KEY, identifier TEXT)",
                "INSERT INTO team VALUES('orphan', 'OLD')",
                "CREATE TABLE user_team(user_id TEXT, team_id TEXT, role TEXT, PRIMARY KEY(user_id,team_id))",
                "INSERT INTO user_team VALUES('legacy','orphan','MEMBER')");
        runMigration();
        runMigration();
        assertEquals(11, readSchemaVersion());
        assertTrue(columnExists("team", "status"));
        assertTrue(columnExists("team", "description"));
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT ut.role, u.global_role, t.status FROM user_team ut JOIN user u ON u.id=ut.user_id JOIN team t ON t.id=ut.team_id")) {
                assertTrue(rs.next());
                assertEquals("EDITOR", rs.getString(1));
                assertEquals("USER", rs.getString(2));
                assertEquals("ACTIVE", rs.getString(3));
                org.junit.jupiter.api.Assertions.assertFalse(rs.next());
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM security_guard WHERE id=1")) {
                assertTrue(rs.next()); assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void v10ReplacesWholePathIndexAndCapturesOnlyTruthfulUpgradeBaseline() throws Exception {
        setupFullSchema();
        execSql("INSERT INTO api_definition(id,team_id,name,method,path,type,response_body,created_at,updated_at) "
                        + "VALUES('api','legacy','Old','GET','/reuse','REST','old-body','original-created','original-updated')",
                "CREATE UNIQUE INDEX idx_api_definition_team_path_method ON api_definition(team_id,path,method)");
        runMigration();runMigration();
        org.springframework.jdbc.core.JdbcTemplate jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        String index=jdbc.queryForObject("SELECT sql FROM sqlite_master WHERE name='idx_api_definition_team_path_method'",String.class);
        assertTrue(index.contains("WHERE deleted_at IS NULL"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM api_revision WHERE api_id='api'",Integer.class));
        assertEquals("MIGRATION_BASELINE",jdbc.queryForObject("SELECT action FROM api_revision WHERE api_id='api'",String.class));
        org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT actor_id FROM api_revision WHERE api_id='api'",String.class));
        assertEquals("original-updated",jdbc.queryForObject("SELECT updated_at FROM api_definition WHERE id='api'",String.class));
        assertTrue(jdbc.queryForObject("SELECT snapshot_json FROM api_revision WHERE api_id='api'",String.class).contains("old-body"));
        execSql("UPDATE api_definition SET deleted_at='now' WHERE id='api'",
                "INSERT INTO api_definition(id,team_id,name,method,path) VALUES('replacement','legacy','New','GET','/reuse')");
        runMigration();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM api_definition",Integer.class));
    }

    // ---------- case2：v1.4.3 发版老库 ----------

    @Test
    void case2_v143LegacyDb_addsDescriptionAndMigratesData() throws Exception {
        setupV143LegacyDbWithData();

        runMigration();

        assertTrue(columnExists("api_definition", "description"),
                "api_definition.description 列应在迁移后存在");
        assertTrue(columnExists("api_definition", "group_id"),
                "api_definition.group_id 列应被 v2 迁移幂等添加（兜底早期开发版）");
        // REST 1 条 + SOAP 1 个 operation = 共 2 条 api_response
        assertEquals(2, countApiResponse(),
                "REST 响应 + SOAP operation 响应应被迁入 api_response 表");
        assertTrue(columnExists("api_response", "body_type"),
                "api_response.body_type 列应在 v4 迁移后存在");
        assertTrue(columnExists("api_definition", "response_mode"),
                "api_definition.response_mode 列应在 v5 迁移后存在");
        assertEquals(11, readSchemaVersion(), "迁移完成后 schema_version 应记为 10（v1 至 v10）");
    }

    // ---------- case3：已跑过 soap-mock-enhancement 的本地库 ----------

    @Test
    void case3_dataAlreadyMigrated_idempotentNoDuplicate() throws Exception {
        setupFullSchema();
        // 模拟已迁移过的状态：api_definition + api_response 均已有数据
        execSql(
                "INSERT INTO api_definition(id, type, response_code, content_type, " +
                        "response_body, delay_ms, created_at, updated_at, description) VALUES (" +
                        "'api-rest-1', 'REST', 200, 'application/json', '{\"ok\":true}', 50, " +
                        "'2026-01-01', '2026-01-01', '')",
                "INSERT INTO api_response(id, api_id, soap_operation_name, name, response_code, " +
                        "content_type, response_body, delay_ms, is_active, sort_order, created_at, " +
                        "updated_at) VALUES ('resp1', 'api-rest-1', NULL, 'Default', 200, " +
                        "'application/json', '{\"ok\":true}', 50, 1, 0, '2026-01-01', '2026-01-01')"
        );

        runMigration();

        assertEquals(1, countApiResponse(), "已迁移的数据不应被重复插入");
        assertEquals(11, readSchemaVersion());
    }

    // ---------- case4：二次启动幂等 ----------

    @Test
    void case4_secondRun_isNoop() throws Exception {
        setupV143LegacyDbWithData();
        runMigration();
        int firstRunResponseCount = countApiResponse();

        runMigration();

        assertEquals(firstRunResponseCount, countApiResponse(),
                "二次迁移不应新增 api_response 数据");
        assertEquals(11, readSchemaVersion());
        assertEquals(11, countSchemaVersionRows(),
                "schema_version 应有六条记录：version=1 至 7，二次执行不重复写入");
    }
}
