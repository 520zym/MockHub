package com.mockhub.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite 数据源配置：
 * 1. 启动时创建 data/ 和 data/wsdl/ 目录
 * 2. 创建 DataSource bean（确保目录在连接前已存在）
 * 3. 设置 WAL 模式提高并发读写性能
 * 4. 执行 schema.sql 建表（IF NOT EXISTS 保证幂等）
 * 5. 按 schema_version 表幂等执行 DB 迁移（v1.4.4 引入版本化机制）
 *
 * 所有数据库初始化在 DataSource bean 创建时同步完成，
 * 确保其他 bean（如 InitService、LogServiceImpl）在 @PostConstruct 中使用数据库时表已就绪。
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);
    private static final int CURRENT_SCHEMA_VERSION = 12;

    @Autowired
    private DataProperties dataProperties;

    /**
     * 自定义 DataSource bean：
     * 1. 先创建数据目录
     * 2. 创建连接池
     * 3. 同一事务内验证旧库、执行 schema.sql 建表及版本迁移
     * 4. 迁移成功后启用 WAL 模式
     */
    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        createDataDirectories();

        DataSource ds = properties.initializeDataSourceBuilder().build();

        // 建表、补列、数据迁移及补索引必须使用同一事务；失败不能留下半升级的 schema。
        executeMigrations(ds);
        // 未知未来版本或无效角色拒绝启动时，连 journal_mode 也不要改动。
        enableWalMode(new JdbcTemplate(ds));

        return ds;
    }

    /**
     * 创建数据目录和 WSDL 子目录
     */
    private void createDataDirectories() {
        File dataDir = new File(dataProperties.getPath());
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            log.info("创建数据目录 {}：{}", dataDir.getAbsolutePath(), created ? "成功" : "失败");
        }

        File wsdlDir = new File(dataDir, "wsdl");
        if (!wsdlDir.exists()) {
            boolean created = wsdlDir.mkdirs();
            log.info("创建 WSDL 目录 {}：{}", wsdlDir.getAbsolutePath(), created ? "成功" : "失败");
        }

        File filesDir = new File(dataDir, "files");
        if (!filesDir.exists()) {
            boolean created = filesDir.mkdirs();
            log.info("创建 Mock 文件目录 {}：{}", filesDir.getAbsolutePath(), created ? "成功" : "失败");
        }
    }

    /**
     * 启用 SQLite WAL 模式，提高并发读写性能
     */
    private void enableWalMode(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("PRAGMA journal_mode=WAL;");
        log.info("SQLite WAL 模式已启用");
    }

    /**
     * 读取 classpath:schema.sql 并执行建表语句
     */
    private void executeSchemaSql(Connection conn, boolean deferMissingColumnIndexes) throws SQLException {
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            InputStream inputStream = resource.getInputStream();
            String schemaSql = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            inputStream.close();

            // 先移除所有 SQL 注释行，再按分号拆分逐条执行
            StringBuilder cleaned = new StringBuilder();
            for (String line : schemaSql.split("\n")) {
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("--")) {
                    cleaned.append(line).append("\n");
                }
            }
            String[] statements = cleaned.toString().split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    try (Statement st = conn.createStatement()) {
                        st.execute(trimmed);
                    }
                } catch (SQLException e) {
                    String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
                    if (deferMissingColumnIndexes && e.getMessage() != null
                            && e.getMessage().contains("no such column")
                            && (upper.startsWith("CREATE INDEX") || upper.startsWith("CREATE UNIQUE INDEX"))) {
                        // 极老版本的 api_definition 可能还没有 group_id 等列，先跳过索引，
                        // 等 migrate/ensureCompatibilityColumns 补列后由第二次 schema.sql 补建。
                        log.warn("schema.sql 暂跳过索引（列尚未补齐）：{}", trimmed);
                    } else {
                        log.error("schema.sql 执行失败：{}", trimmed, e);
                        throw e;
                    }
                }
            }
            log.info("schema.sql 执行完成，数据库表已就绪");
        } catch (IOException e) {
            log.error("读取 schema.sql 失败", e);
            throw new RuntimeException("无法初始化数据库", e);
        }
    }

    /**
     * 执行 DB 迁移（v1.4.4 起版本化）：
     * <ol>
     *   <li>读取 schema_version 表获取已应用的最大版本号</li>
     *   <li>按 if (current &lt; N) 分发器逐版本执行 migrateVN</li>
     *   <li>每个 migrateVN 执行成功后写入 schema_version</li>
     *   <li>整个迁移过程包在事务中，任一环节失败全部回滚</li>
     * </ol>
     *
     * <p>迁移失败必须阻止启动，避免在缺少认证状态/会话结构时继续服务。老库升级路径：
     * <ul>
     *   <li>v1.4.3 发版用户：schema_version 表初始为空（current=0）→ 执行 migrateV1 真实建列/迁移 → 记录 version=1</li>
     *   <li>已在 main 跑过 soap-mock-enhancement 的开发者本地库：列/表已存在，migrateV1 幂等跳过 → 记录 version=1</li>
     *   <li>全新库：migrateV1 幂等执行（列不存在就加，数据为空就跳过）→ 记录 version=1</li>
     * </ul>
     */
    private void executeMigrations(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long current = getCurrentSchemaVersion(conn);
                log.info("DB 当前 schema 版本：v{}", current);
                if (current > CURRENT_SCHEMA_VERSION) {
                    throw new IllegalStateException("数据库 schema v" + current + " 高于本程序支持的 v"
                            + CURRENT_SCHEMA_VERSION + "；请使用匹配的新版本程序，禁止降级或修改版本记录");
                }
                validateLegacyRoles(conn);
                executeSchemaSql(conn, true);
                // 在 V1 查询/迁入响应前补齐已知旧库可缺少的兼容列；不覆盖任何正文。
                ensureCompatibilityColumns(conn);

                if (current < 1) {
                    migrateV1(conn);
                    recordSchemaVersion(conn, 1,
                            "初始基线：api_definition.description 列 + api_response 表 + REST/SOAP 响应迁移");
                }
                if (current < 2) {
                    migrateV2(conn);
                    recordSchemaVersion(conn, 2,
                            "接口分组兜底：api_definition.group_id 列幂等添加（防御早期开发版升级）");
                }
                if (current < 3) {
                    migrateV3(conn);
                    recordSchemaVersion(conn, 3,
                            "接口调用统计：api_definition 增加 hit_count 与 last_called_at 列");
                }
                if (current < 4) {
                    migrateV4(conn);
                    recordSchemaVersion(conn, 4,
                            "文件响应模拟：api_response 增加 body_type 与文件元数据列");
                }
                if (current < 5) {
                    migrateV5(conn);
                    recordSchemaVersion(conn, 5,
                            "多返回体随机模式：api_definition 增加 response_mode 列");
                }
                if (current < 6) {
                    migrateV6(conn);
                    recordSchemaVersion(conn, 6,
                            "AI 供应商：ai_settings 增加 vendor 列，并按已有 Base URL 回填");
                }
                if (current < 7) {
                    migrateV7(conn);
                    recordSchemaVersion(conn, 7,
                            "MCP 工具：mcp_settings 增加 tool_update_api_draft 开关");
                }
                if (current < 8) {
                    migrateV8(conn);
                    recordSchemaVersion(conn, 8, "账号状态、认证版本与网页认证会话");
                }
                if (current < 9) {
                    migrateV9(conn);
                    recordSchemaVersion(conn, 9, "团队角色、归档状态与管理员安全写锁");
                }
                migrateV3(conn); // 已知高版本缺列形态也必须补回统计索引。
                ensureLifecycleSchema(conn);
                if (current < 10) {
                    migrateV10(conn);
                    recordSchemaVersion(conn, 10, "接口聚合版本、并发校验与30天回收站");
                }
                migrateV11(conn);
                if (current < 11) recordSchemaVersion(conn, 11, "MCP个人凭证显式团队能力范围与草稿来源");
                if (current < 12) {
                    migrateV12(conn);
                    recordSchemaVersion(conn, 12, "文件标签并入团队共享标签；文件下载计入请求日志");
                }
                executeSchemaSql(conn, false);
                conn.commit();
                log.info("DB 迁移完成");
            } catch (Exception e) {
                safeRollback(conn);
                String detail = e instanceof IllegalStateException ? "：" + e.getMessage() : "";
                throw new IllegalStateException("DB 迁移失败，已回滚，拒绝启动" + detail, e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("获取数据库连接失败，拒绝跳过迁移", e);
        }
    }

    /**
     * 查询当前已应用的最大 schema 版本号。
     * 表不存在或为空时返回 0，代表尚未进入版本化体系。
     */
    private long getCurrentSchemaVersion(Connection conn) throws SQLException {
        // 必须在执行当前 schema.sql 前读取，才能无写入地拒绝未知未来版本。
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return 0;
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version), 0) FROM schema_version");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 将已执行的迁移版本写入 schema_version 表。
     * 使用 INSERT OR IGNORE，即使该版本已被误记录也不报 PRIMARY KEY 冲突。
     */
    private void recordSchemaVersion(Connection conn, int version, String description) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO schema_version(version, description) VALUES(?, ?)")) {
            ps.setInt(1, version);
            ps.setString(2, description);
            ps.executeUpdate();
        }
        log.info("DB schema 升级到 v{}: {}", version, description);
    }

    /**
     * 迁移 V1（合并 v1.4.3~v1.4.4 期间的三步零散迁移）：
     * <ol>
     *   <li>api_definition 表添加 description 列</li>
     *   <li>REST 接口旧响应数据迁入 api_response 表</li>
     *   <li>SOAP operation 旧响应数据迁入 api_response 表</li>
     * </ol>
     * 所有步骤均幂等，即使表/列已存在、数据已迁移，二次执行也不会破坏数据。
     */
    private void migrateV1(Connection conn) throws SQLException {
        // 1. 为旧版数据库添加 description 列
        addColumnIfNotExists(conn, "api_definition", "description", "TEXT");

        // 2. REST 接口数据迁移 —— WHERE NOT IN 保证幂等
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO api_response (id, api_id, soap_operation_name, name, response_code, " +
                "content_type, response_body, delay_ms, is_active, sort_order, created_at, updated_at) " +
                "SELECT lower(hex(randomblob(16))), id, NULL, 'Default', response_code, " +
                "content_type, response_body, delay_ms, 1, 0, created_at, updated_at " +
                "FROM api_definition WHERE type = 'REST' " +
                "AND id NOT IN (SELECT DISTINCT api_id FROM api_response WHERE soap_operation_name IS NULL)")) {
            int migratedRest = ps.executeUpdate();
            if (migratedRest > 0) {
                log.info("REST 接口响应数据迁移完成，迁移 {} 条", migratedRest);
            }
        }

        // 3. SOAP operation 响应数据迁移
        migrateSoapResponses(conn);
    }

    /**
     * v2 迁移（接口分组兜底）：幂等添加 api_definition.group_id 列。
     * <p>
     * 背景：schema.sql 自首次引入即包含 api_group 表和 group_id 列，
     * 所有 v1.x 用户库通过 CREATE TABLE IF NOT EXISTS 已经覆盖，
     * 这里仅为防御从更早开发版升级的边缘场景。
     * <p>
     * 注：api_group 表无需迁移——CREATE TABLE IF NOT EXISTS 在 schema.sql 中
     * 已能为老库创建该表；但既有 api_definition 表的 group_id 列必须靠 ALTER 补上。
     */
    private void migrateV2(Connection conn) throws SQLException {
        addColumnIfNotExists(conn, "api_definition", "group_id", "TEXT");
    }

    /**
     * v3 迁移（接口调用统计）：幂等添加 api_definition.hit_count 与 last_called_at 列，
     * 并补建 last_called_at 索引以支撑僵尸接口查询。
     * <p>
     * hit_count：累计命中次数，从 0 起算，永久累加，不受请求日志清理影响；
     * last_called_at：最近一次被命中的时间（ISO 格式），从未命中则为 null。
     * <p>
     * 所有步骤幂等：列已存在跳过 ALTER；CREATE INDEX IF NOT EXISTS 自身幂等。
     */
    private void migrateV3(Connection conn) throws SQLException {
        addColumnIfNotExists(conn, "api_definition", "hit_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfNotExists(conn, "api_definition", "last_called_at", "TEXT");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_api_definition_last_called_at " +
                    "ON api_definition(last_called_at)");
        }
    }

    /**
     * v4 迁移（文件响应模拟）：幂等添加 api_response 的响应体类型与文件元数据列。
     * <p>
     * 老数据默认 body_type=TEXT，继续按原 response_body 文本响应工作。
     */
    private void migrateV4(Connection conn) throws SQLException {
        addColumnIfNotExists(conn, "api_response", "body_type", "TEXT NOT NULL DEFAULT 'TEXT'");
        addColumnIfNotExists(conn, "api_response", "file_name", "TEXT");
        addColumnIfNotExists(conn, "api_response", "file_path", "TEXT");
        addColumnIfNotExists(conn, "api_response", "download_name", "TEXT");
        addColumnIfNotExists(conn, "api_response", "file_size", "INTEGER");
    }

    /** v5 迁移：REST 接口增加多返回体选择模式，老数据保持条件匹配。 */
    private void migrateV5(Connection conn) throws SQLException {
        addColumnIfNotExists(conn, "api_definition", "response_mode",
                "TEXT NOT NULL DEFAULT 'CONDITION'");
    }

    /**
     * v6 迁移：AI 供应商开关。为 ai_settings 增加 vendor 列，
     * 并按已有 Base URL 推断旧配置的供应商，避免升级后卡片全部落到「自定义」。
     * 表不存在时直接跳过（单元测试/异常场景兜底）。
     */
    private void migrateV6(Connection conn) throws SQLException {
        if (!tableExists(conn, "ai_settings")) {
            log.info("ai_settings 表不存在，跳过 v6 AI 供应商迁移");
            return;
        }
        addColumnIfNotExists(conn, "ai_settings", "vendor", "TEXT NOT NULL DEFAULT 'CUSTOM'");
        try (Statement st = conn.createStatement()) {
            st.execute("UPDATE ai_settings SET vendor = CASE "
                    + "WHEN lower(base_url) LIKE '%api.deepseek.com%' THEN 'deepseek' "
                    + "WHEN lower(base_url) LIKE '%moonshot%' THEN 'kimi' "
                    + "WHEN lower(base_url) LIKE '%minimax%' THEN 'minimax' "
                    + "WHEN lower(base_url) LIKE '%bigmodel%' THEN 'glm' "
                    + "WHEN lower(base_url) LIKE '%token-plan%qianwenaiapi%' THEN 'qwen-token-plan' "
                    + "WHEN lower(base_url) LIKE '%qianwenaiapi%' OR lower(base_url) LIKE '%dashscope%' THEN 'qwen' "
                    + "WHEN lower(base_url) LIKE '%volces.com%' THEN 'volcengine' "
                    + "WHEN lower(base_url) LIKE '%qianfan.baidubce.com%' THEN 'qianfan' "
                    + "WHEN lower(base_url) LIKE '%hunyuan.cloud.tencent.com%' OR lower(base_url) LIKE '%tokenhub%' THEN 'hunyuan' "
                    + "WHEN lower(base_url) LIKE '%siliconflow%' THEN 'siliconflow' "
                    + "WHEN lower(base_url) LIKE '%api.openai.com%' THEN 'openai' "
                    + "WHEN lower(base_url) LIKE '%11434%' THEN 'ollama' "
                    + "ELSE 'custom' END "
                    + "WHERE vendor IS NULL OR vendor = '' OR upper(vendor) = 'CUSTOM'");
        }
    }

    /** v7：MCP 增加「修改接口草稿」工具开关。 */
    private void migrateV7(Connection conn) throws SQLException {
        if (!tableExists(conn, "mcp_settings")) {
            log.info("mcp_settings 表不存在，跳过 v7 迁移");
            return;
        }
        addColumnIfNotExists(conn, "mcp_settings", "tool_update_api_draft", "INTEGER NOT NULL DEFAULT 1");
    }

    /** V8 在旧库先补列，新库同样幂等。任何 DDL 错误均由迁移事务回滚并拒绝启动。 */
    private void migrateV8(Connection conn) throws SQLException {
        addColumnIfNotExists(conn, "user", "status",
                "TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED'))");
        addColumnIfNotExists(conn, "user", "auth_version",
                "INTEGER NOT NULL DEFAULT 0 CHECK (auth_version >= 0)");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS auth_session ("
                    + "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, auth_version INTEGER NOT NULL, "
                    + "created_at TEXT NOT NULL, expires_at INTEGER NOT NULL, revoked_at TEXT, "
                    + "user_agent TEXT, ip_address TEXT, "
                    + "FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_auth_session_user ON auth_session(user_id)");
            // 即使有人误写高版本号，也不能跳过安全结构检查。
            st.executeQuery("SELECT status, auth_version FROM user LIMIT 0").close();
            st.executeQuery("SELECT id, user_id, auth_version, created_at, expires_at, revoked_at, "
                    + "user_agent, ip_address FROM auth_session LIMIT 0").close();
        }
    }

    /** Only documented historical aliases are accepted. Never infer an editable role from unknown data. */
    private void validateLegacyRoles(Connection conn) throws SQLException {
        List<String> problems = new ArrayList<>();
        validateRoleColumn(conn, "user", "global_role", "id", null,
                new String[]{"SUPER_ADMIN", "USER", "MEMBER", "TEAM_ADMIN"}, problems);
        validateRoleColumn(conn, "user_team", "role", "user_id", "team_id",
                new String[]{"TEAM_ADMIN", "EDITOR", "VIEWER", "MEMBER"}, problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("角色兼容检查失败；未修改数据库。请备份数据库，根据账号/团队的真实权限人工修复以下记录后重启；"
                    + "不要将未知值默认设为 EDITOR/管理员。合法全局角色 SUPER_ADMIN/USER（旧 MEMBER/TEAM_ADMIN→USER），"
                    + "团队角色 TEAM_ADMIN/EDITOR/VIEWER（旧 MEMBER→EDITOR）：" + String.join("; ", problems));
        }
    }

    private void validateRoleColumn(Connection conn, String table, String column, String idColumn,
                                    String secondIdColumn, String[] allowed, List<String> problems) throws SQLException {
        if (!tableExists(conn, table)) return;
        boolean hasRole = false;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equals(rs.getString("name"))) hasRole = true;
        }
        if (!hasRole) {
            problems.add(table + "." + column + " 缺列（不能推断权限，请恢复历史角色列）");
            return;
        }
        String ids = idColumn + (secondIdColumn == null ? "" : "," + secondIdColumn);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + ids + "," + column + " FROM " + table)) {
            while (rs.next()) {
                String role = rs.getString(column);
                boolean known = false;
                for (String value : allowed) if (value.equals(role)) known = true;
                if (!known) {
                    problems.add(table + "[" + idColumn + "=" + reportValue(rs.getString(idColumn))
                            + (secondIdColumn == null ? "" : "," + secondIdColumn + "=" + reportValue(rs.getString(secondIdColumn)))
                            + "]." + column + "=" + reportValue(role));
                }
            }
        }
    }

    /** Report only identifiers/roles, not complete rows (password hashes, API keys or token hashes). */
    private String reportValue(String value) {
        if (value == null) return "NULL";
        return "'" + value.replace("\\", "\\\\").replace("'", "''")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "'";
    }

    /** V9 不随机修复孤儿团队；保留全部成员关系，由超管明确指派管理员。 */
    private void migrateV9(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS security_guard (id INTEGER PRIMARY KEY CHECK(id = 1), revision INTEGER NOT NULL DEFAULT 0)");
            st.execute("INSERT OR IGNORE INTO security_guard(id, revision) VALUES(1, 0)");
        }
        if (tableExists(conn, "team")) {
            addColumnIfNotExists(conn, "team", "status", "TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'ARCHIVED'))");
            addColumnIfNotExists(conn, "team", "description", "TEXT");
            addColumnIfNotExists(conn, "team", "created_by", "TEXT");
        }
        if (tableExists(conn, "user_team")) {
            try (Statement st = conn.createStatement()) {
                st.execute("UPDATE user_team SET role = 'EDITOR' WHERE role = 'MEMBER'");
            }
        }
        // 已在事务入口验证真实历史枚举；只转换有依据的旧别名。
        if (tableExists(conn, "user")) {
            try (Statement st = conn.createStatement()) {
                st.execute("UPDATE user SET global_role = 'USER' WHERE global_role IN ('MEMBER', 'TEAM_ADMIN')");
            }
        }
    }

    /**
     * 老版本兼容兜底：不依赖 schema_version，逐列补齐当前代码会读取的字段。
     * <p>
     * 老用户可能来自任意历史版本，甚至没有完整的版本记录；这里只做幂等补列，
     * 不修改已有数据，保证升级后不用手工改库。
     */
    /** Fail closed: no implicit full-team upgrade for old credentials or unbound pending actions. */
    private void migrateV11(Connection conn) throws SQLException {
        if (tableExists(conn, "mcp_token")) {
            addColumnIfNotExists(conn, "mcp_token", "scope_version", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfNotExists(conn, "mcp_token", "team_ids", "TEXT");
            addColumnIfNotExists(conn, "mcp_token", "scopes", "TEXT");
            try (Statement st = conn.createStatement()) {
                st.execute("UPDATE mcp_token SET revoked_at=COALESCE(revoked_at, datetime('now')) WHERE scope_version<>1 OR team_ids IS NULL OR scopes IS NULL");
            }
        }
        if (tableExists(conn, "ai_pending_action")) {
            addColumnIfNotExists(conn, "ai_pending_action", "source_type", "TEXT NOT NULL DEFAULT 'LEGACY'");
            addColumnIfNotExists(conn, "ai_pending_action", "source_token_id", "TEXT");
            addColumnIfNotExists(conn, "ai_pending_action", "source_scope_json", "TEXT");
        }
    }

    /**
     * v12：
     * 1) 文件服务器标签由自由文本并入团队共享标签表 tag（与接口标签同一套）；
     * 2) request_log 增加 target_type，区分 Mock 接口调用与文件下载。
     * 迁移只搬运已存在的文本标签，不改写任何文件名、别名或下载计数。
     */
    private void migrateV12(Connection conn) throws SQLException {
        if (tableExists(conn, "request_log")) {
            addColumnIfNotExists(conn, "request_log", "target_type", "TEXT NOT NULL DEFAULT 'API'");
        }
        if (!tableExists(conn, "file_server_tag") || !hasColumn(conn, "file_server_tag", "tag")) {
            return;
        }
        java.util.Map<String, String> teamByFile = new java.util.HashMap<>();
        if (tableExists(conn, "file_server_file")) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT file_id, team_id FROM file_server_file")) {
                while (rs.next()) {
                    teamByFile.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS file_server_tag_shared (file_id TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY (file_id, tag_id))");
        }
        int migrated = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT file_id, tag FROM file_server_tag")) {
            while (rs.next()) {
                String fileId = rs.getString(1);
                String name = rs.getString(2);
                String teamId = teamByFile.get(fileId);
                if (teamId == null || name == null || name.trim().isEmpty()) {
                    continue;
                }
                String tagId = findOrCreateSharedTag(conn, teamId, name.trim());
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO file_server_tag_shared(file_id, tag_id) VALUES(?, ?)")) {
                    ps.setString(1, fileId);
                    ps.setString(2, tagId);
                    ps.executeUpdate();
                }
                migrated++;
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE file_server_tag");
            st.execute("ALTER TABLE file_server_tag_shared RENAME TO file_server_tag");
            st.execute("CREATE INDEX IF NOT EXISTS idx_file_server_tag_tag_id ON file_server_tag(tag_id)");
        }
        log.info("数据库迁移：{} 条文件标签并入团队共享标签", migrated);
    }

    /** 在团队内按名称复用共享标签；不存在时按名称哈希取一个稳定颜色新建。 */
    private String findOrCreateSharedTag(Connection conn, String teamId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM tag WHERE team_id = ? AND lower(name) = lower(?) LIMIT 1")) {
            ps.setString(1, teamId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        String id = java.util.UUID.randomUUID().toString();
        String[] palette = {"#3B6D11", "#3568D4", "#B45309", "#933B36", "#6D28D9", "#0F766E"};
        String color = palette[Math.abs(name.hashCode()) % palette.length];
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tag(id, team_id, name, color) VALUES(?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, teamId);
            ps.setString(3, name);
            ps.setString(4, color);
            ps.executeUpdate();
        }
        return id;
    }

    private void ensureCompatibilityColumns(Connection conn) throws SQLException {
        migrateV8(conn);
        migrateV9(conn);
        ensureColumns(conn, "api_definition", new String[][]{
                {"description", "TEXT"}, {"group_id", "TEXT"}, {"scenarios", "TEXT"},
                {"response_mode", "TEXT NOT NULL DEFAULT 'CONDITION'"},
                {"hit_count", "INTEGER NOT NULL DEFAULT 0"}, {"last_called_at", "TEXT"},
                {"global_header_overrides", "TEXT"}, {"soap_config", "TEXT"},
                {"created_by", "TEXT"}, {"updated_by", "TEXT"}
        });
        ensureColumns(conn, "api_response", new String[][]{
                {"body_type", "TEXT NOT NULL DEFAULT 'TEXT'"}, {"file_name", "TEXT"},
                {"file_path", "TEXT"}, {"download_name", "TEXT"}, {"file_size", "INTEGER"},
                {"sort_order", "INTEGER NOT NULL DEFAULT 0"}, {"conditions", "TEXT"},
                {"delay_ms", "INTEGER NOT NULL DEFAULT 0"}, {"is_active", "INTEGER NOT NULL DEFAULT 0"}
        });
        ensureColumns(conn, "ai_settings", new String[][]{
                {"enabled", "INTEGER NOT NULL DEFAULT 0"},
                {"provider", "TEXT NOT NULL DEFAULT 'OPENAI_COMPATIBLE'"},
                {"vendor", "TEXT NOT NULL DEFAULT 'CUSTOM'"}, {"base_url", "TEXT"},
                {"model", "TEXT"}, {"api_key", "TEXT"},
                {"cap_response_body", "INTEGER NOT NULL DEFAULT 1"},
                {"cap_conditions", "INTEGER NOT NULL DEFAULT 1"},
                {"cap_explain", "INTEGER NOT NULL DEFAULT 1"},
                {"cap_drafts", "INTEGER NOT NULL DEFAULT 1"},
                {"access_scope", "TEXT NOT NULL DEFAULT 'ALL_MEMBERS'"},
                {"updated_at", "TEXT"}, {"updated_by", "TEXT"}
        });
        ensureColumns(conn, "ai_pending_action", new String[][]{
                {"team_id", "TEXT"}, {"summary", "TEXT"}, {"confirmed_at", "TEXT"},
                {"result_api_id", "TEXT"}
        });
        ensureColumns(conn, "mcp_settings", new String[][]{
                {"enabled", "INTEGER NOT NULL DEFAULT 0"},
                {"write_mode", "TEXT NOT NULL DEFAULT 'DRAFT_ONLY'"},
                {"tool_search_apis", "INTEGER NOT NULL DEFAULT 1"},
                {"tool_get_api_detail", "INTEGER NOT NULL DEFAULT 1"},
                {"tool_get_api_stats", "INTEGER NOT NULL DEFAULT 1"},
                {"tool_create_api_draft", "INTEGER NOT NULL DEFAULT 1"},
                {"tool_update_api_draft", "INTEGER NOT NULL DEFAULT 1"},
                {"tool_apply_api_draft", "INTEGER NOT NULL DEFAULT 0"},
                {"tool_generate_response_body", "INTEGER NOT NULL DEFAULT 1"},
                {"tool_generate_conditions", "INTEGER NOT NULL DEFAULT 1"},
                {"tool_explain_match_result", "INTEGER NOT NULL DEFAULT 1"},
                {"updated_at", "TEXT"}, {"updated_by", "TEXT"}
        });
    }

    private void ensureLifecycleSchema(Connection conn) throws SQLException {
        addColumnIfNotExists(conn, "api_definition", "revision", "INTEGER NOT NULL DEFAULT 1 CHECK(revision >= 1)");
        addColumnIfNotExists(conn, "api_definition", "deleted_at", "TEXT");
        addColumnIfNotExists(conn, "api_definition", "deleted_by", "TEXT");
        addColumnIfNotExists(conn, "api_definition", "purge_after", "TEXT");
        if (tableExists(conn, "ai_pending_action")) {
            addColumnIfNotExists(conn,"ai_pending_action","base_revision","INTEGER");
            addColumnIfNotExists(conn,"ai_pending_action","target_api_id","TEXT");
        }
        try (Statement st=conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS api_revision (\n    id TEXT PRIMARY KEY, api_id TEXT NOT NULL, team_id TEXT NOT NULL, revision INTEGER NOT NULL,\n    action TEXT NOT NULL, actor_id TEXT, actor_name_snapshot TEXT NOT NULL, created_at TEXT NOT NULL,\n    source TEXT NOT NULL, snapshot_json TEXT NOT NULL, snapshot_schema_version INTEGER NOT NULL DEFAULT 1,\n    restored_from_revision_id TEXT, UNIQUE(api_id, revision)\n)");
            st.execute("CREATE TABLE IF NOT EXISTS api_tombstone (api_id TEXT PRIMARY KEY, team_id TEXT NOT NULL, name TEXT, method TEXT, path TEXT, revision INTEGER NOT NULL, deleted_at TEXT, deleted_by TEXT, purged_at TEXT NOT NULL, purged_by TEXT)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_api_revision_api ON api_revision(api_id, revision DESC)");
            st.execute("CREATE TABLE IF NOT EXISTS api_lifecycle_lock (id INTEGER PRIMARY KEY CHECK(id=1), marker INTEGER NOT NULL DEFAULT 0, history_delete_allowed INTEGER NOT NULL DEFAULT 0)");
            st.execute("INSERT OR IGNORE INTO api_lifecycle_lock(id,marker) VALUES(1,0)");
            st.execute("CREATE TABLE IF NOT EXISTS mock_blob (file_path TEXT PRIMARY KEY, team_id TEXT NOT NULL, lease_expires_at INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS mock_blob_ref (file_path TEXT NOT NULL, owner_type TEXT NOT NULL, owner_id TEXT NOT NULL,\n    expires_at INTEGER, PRIMARY KEY(file_path,owner_type,owner_id))");
            st.execute("CREATE INDEX IF NOT EXISTS idx_mock_blob_ref_owner ON mock_blob_ref(owner_type,owner_id)");
            addColumnIfNotExists(conn,"api_lifecycle_lock","history_delete_allowed","INTEGER NOT NULL DEFAULT 0");
            st.execute("DROP TRIGGER IF EXISTS api_revision_protected_delete");
            st.execute("CREATE TRIGGER IF NOT EXISTS api_revision_protected_delete BEFORE DELETE ON api_revision "
                    + "WHEN COALESCE((SELECT history_delete_allowed FROM api_lifecycle_lock WHERE id=1),0)=0 "
                    + "BEGIN SELECT RAISE(ABORT,'Revision deletion requires audited retention or purge'); END");
            st.execute("CREATE TRIGGER IF NOT EXISTS api_revision_immutable BEFORE UPDATE ON api_revision "
                    + "BEGIN SELECT RAISE(ABORT,'Published API revisions are immutable'); END");
            st.execute("SELECT id,api_id,team_id,revision,action,actor_id,actor_name_snapshot,created_at,source,snapshot_json,"
                    + "snapshot_schema_version,restored_from_revision_id FROM api_revision LIMIT 0");
            st.execute("DROP INDEX IF EXISTS idx_api_definition_team_path_method");
            st.execute("CREATE UNIQUE INDEX idx_api_definition_team_path_method ON api_definition(team_id,path,method) WHERE deleted_at IS NULL");
        }
    }

    private void migrateV10(Connection conn) throws SQLException {
        backfillLegacyDraftTargets(conn);
        java.util.List<java.util.Map<String,Object>> apis = com.mockhub.mock.lifecycle.ApiSnapshot.query(conn,
                "SELECT id,team_id,revision FROM api_definition");
        for (java.util.Map<String,Object> api : apis) {
            String id = api.get("id").toString();
            String snapshot = com.mockhub.mock.lifecycle.ApiSnapshot.capture(conn,id);
            String revisionId=UUID.randomUUID().toString();
            try (PreparedStatement ps=conn.prepareStatement("INSERT OR IGNORE INTO api_revision "
                    + "(id,api_id,team_id,revision,action,actor_name_snapshot,created_at,source,snapshot_json) "
                    + "VALUES(?,?,?,?, 'MIGRATION_BASELINE','系统迁移',?,'MIGRATION',?)")) {
                ps.setString(1,revisionId); ps.setString(2,id);
                ps.setObject(3,api.get("team_id")); ps.setObject(4,api.get("revision"));
                ps.setString(5,java.time.Instant.now().toString()); ps.setString(6,snapshot); ps.executeUpdate();
                com.mockhub.mock.lifecycle.ApiSnapshot.protectBlobs(conn,api.get("team_id").toString(),id,revisionId,snapshot);
            }
        }
    }

    private void backfillLegacyDraftTargets(Connection conn) throws SQLException {
        if(!tableExists(conn,"ai_pending_action")) return;
        for(Map<String,Object> row:com.mockhub.mock.lifecycle.ApiSnapshot.query(conn,
                "SELECT id,payload FROM ai_pending_action WHERE action_type IN ('UPDATE_API','UPDATE_API_FULL') AND target_api_id IS NULL")) {
            try {
                String target=new ObjectMapper().readTree(row.get("payload").toString()).path("apiId").asText(null);
                if(target!=null) try(PreparedStatement ps=conn.prepareStatement("UPDATE ai_pending_action SET target_api_id=? WHERE id=?")) {
                    ps.setString(1,target);ps.setObject(2,row.get("id"));ps.executeUpdate();
                }
            } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) {
                log.warn("旧AI草稿 {} 内容损坏，取消并清除不可解析正文",row.get("id"));
                try(PreparedStatement ps=conn.prepareStatement("UPDATE ai_pending_action SET status='CANCELLED',payload='{}' WHERE id=?")) {
                    ps.setObject(1,row.get("id"));ps.executeUpdate();
                }
            }
        }
    }

    private void ensureColumns(Connection conn, String table, String[][] columns) throws SQLException {
        if (!tableExists(conn, table)) {
            log.info("兼容检查：{} 表不存在，跳过补列", table);
            return;
        }
        for (String[] column : columns) {
            addColumnIfNotExists(conn, table, column[0], column[1]);
        }
    }

    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 为旧版数据库幂等添加列：先 PRAGMA table_info 检查列是否已存在，不存在才 ALTER TABLE。
     */
    private void addColumnIfNotExists(Connection conn, String table, String column, String type) throws SQLException {
        boolean exists = false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (exists) {
            log.debug("列 {}.{} 已存在，跳过添加", table, column);
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("数据库迁移：{} 表添加 {} 列成功", table, column);
        }
    }

    /**
     * 将 SOAP 接口 soapConfig 中各 operation 的响应数据迁移到 api_response 表。
     * 已迁移过的 operation 会被跳过（通过 COUNT 检查），单个 JSON 解析失败也不影响其他接口。
     */
    @SuppressWarnings("unchecked")
    private void migrateSoapResponses(Connection conn) throws SQLException {
        List<Map<String, Object>> soapApis = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, soap_config, created_at, updated_at FROM api_definition " +
                "WHERE type = 'SOAP' AND soap_config IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("soap_config", rs.getString("soap_config"));
                row.put("created_at", rs.getString("created_at"));
                row.put("updated_at", rs.getString("updated_at"));
                soapApis.add(row);
            }
        }

        if (soapApis.isEmpty()) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        int migratedCount = 0;

        for (Map<String, Object> api : soapApis) {
            String apiId = (String) api.get("id");
            String soapConfigJson = (String) api.get("soap_config");
            String createdAt = (String) api.get("created_at");
            String updatedAt = (String) api.get("updated_at");

            if (soapConfigJson == null || soapConfigJson.trim().isEmpty()) {
                continue;
            }

            try {
                Map<String, Object> soapConfig = mapper.readValue(soapConfigJson,
                        new TypeReference<Map<String, Object>>() {});
                List<Map<String, Object>> operations = (List<Map<String, Object>>) soapConfig.get("operations");
                if (operations == null) {
                    continue;
                }

                for (Map<String, Object> op : operations) {
                    String operationName = (String) op.get("operationName");
                    if (operationName == null) {
                        continue;
                    }

                    if (soapOperationResponseExists(conn, apiId, operationName)) {
                        continue;
                    }

                    int responseCode = op.get("responseCode") != null
                            ? ((Number) op.get("responseCode")).intValue() : 200;
                    int delayMs = op.get("delayMs") != null
                            ? ((Number) op.get("delayMs")).intValue() : 0;
                    String responseBody = (String) op.get("responseBody");

                    try (PreparedStatement insertPs = conn.prepareStatement(
                            "INSERT INTO api_response (id, api_id, soap_operation_name, name, response_code, " +
                            "content_type, response_body, delay_ms, is_active, sort_order, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 'Default', ?, 'text/xml', ?, ?, 1, 0, ?, ?)")) {
                        insertPs.setString(1, UUID.randomUUID().toString());
                        insertPs.setString(2, apiId);
                        insertPs.setString(3, operationName);
                        insertPs.setInt(4, responseCode);
                        insertPs.setString(5, responseBody);
                        insertPs.setInt(6, delayMs);
                        insertPs.setString(7, createdAt);
                        insertPs.setString(8, updatedAt);
                        insertPs.executeUpdate();
                    }
                    migratedCount++;
                }
            } catch (Exception e) {
                // 单条 JSON 解析失败不应拖累整体迁移，但记录 warn 日志便于排查
                log.warn("解析 SOAP 接口 {} 的 soapConfig 失败，跳过迁移: {}", apiId, e.getMessage());
            }
        }

        if (migratedCount > 0) {
            log.info("SOAP operation 响应数据迁移完成，迁移 {} 条", migratedCount);
        }
    }

    /**
     * 判断某个 SOAP operation 是否已有 api_response 记录
     */
    private boolean soapOperationResponseExists(Connection conn, String apiId, String operationName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM api_response WHERE api_id = ? AND soap_operation_name = ?")) {
            ps.setString(1, apiId);
            ps.setString(2, operationName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 事务回滚，失败时记 error 日志（回滚失败本身不应抛出阻止 catch 外层继续打印原始异常）
     */
    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.error("DB 迁移回滚失败", e);
        }
    }
}