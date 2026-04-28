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
                "CREATE TABLE api_definition (id TEXT PRIMARY KEY, type TEXT, " +
                        "response_code INTEGER, content_type TEXT, response_body TEXT, " +
                        "delay_ms INTEGER, soap_config TEXT, created_at TEXT, updated_at TEXT, " +
                        "description TEXT)",
                "CREATE TABLE api_response (id TEXT PRIMARY KEY, api_id TEXT, " +
                        "soap_operation_name TEXT, name TEXT, response_code INTEGER, " +
                        "content_type TEXT, response_body TEXT, delay_ms INTEGER, " +
                        "is_active INTEGER, sort_order INTEGER, created_at TEXT, updated_at TEXT)",
                "CREATE TABLE schema_version (version INTEGER PRIMARY KEY, " +
                        "applied_at TEXT DEFAULT (datetime('now')), description TEXT)"
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
                "CREATE TABLE api_definition (id TEXT PRIMARY KEY, type TEXT, " +
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

        assertEquals(2, readSchemaVersion(), "全新库应记录 schema_version=2（v1+v2）");
        assertTrue(columnExists("api_definition", "description"),
                "description 列应已存在（全新库 schema.sql 已建出）");
        assertTrue(columnExists("api_definition", "group_id"),
                "group_id 列应已被 v2 迁移幂等添加");
        assertEquals(0, countApiResponse(), "全新库无数据，api_response 应为空");
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
        assertEquals(2, readSchemaVersion(), "迁移完成后 schema_version 应记为 2（v1+v2）");
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
        assertEquals(2, readSchemaVersion());
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
        assertEquals(2, readSchemaVersion());
        assertEquals(2, countSchemaVersionRows(),
                "schema_version 应有两条记录：version=1 与 version=2，二次执行不重复写入");
    }
}
