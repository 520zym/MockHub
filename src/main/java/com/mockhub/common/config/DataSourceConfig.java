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

    @Autowired
    private DataProperties dataProperties;

    /**
     * 自定义 DataSource bean：
     * 1. 先创建数据目录
     * 2. 创建连接池
     * 3. 启用 WAL 模式
     * 4. 执行 schema.sql 建表
     * 5. 按 schema_version 执行迁移
     */
    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        createDataDirectories();

        DataSource ds = properties.initializeDataSourceBuilder().build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        enableWalMode(jdbcTemplate);
        executeSchemaSql(jdbcTemplate);
        executeMigrations(ds);

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
    private void executeSchemaSql(JdbcTemplate jdbcTemplate) {
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
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
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
     * <p>迁移失败不会阻止应用启动，但会打 ERROR 日志。老库升级路径：
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
                int current = getCurrentSchemaVersion(conn);
                log.info("DB 当前 schema 版本：v{}", current);

                if (current < 1) {
                    migrateV1(conn);
                    recordSchemaVersion(conn, 1,
                            "初始基线：api_definition.description 列 + api_response 表 + REST/SOAP 响应迁移");
                }
                // 后续版本追加：
                // if (current < 2) { migrateV2(conn); recordSchemaVersion(conn, 2, "..."); }

                conn.commit();
                log.info("DB 迁移完成");
            } catch (Exception e) {
                safeRollback(conn);
                log.error("DB 迁移失败，已回滚。应用继续启动，但请尽快排查。", e);
            }
        } catch (SQLException e) {
            log.error("获取数据库连接失败，跳过迁移", e);
        }
    }

    /**
     * 查询当前已应用的最大 schema 版本号。
     * 表不存在或为空时返回 0，代表尚未进入版本化体系。
     */
    private int getCurrentSchemaVersion(Connection conn) throws SQLException {
        // 兜底检查表存在性：正常情况下 schema.sql 刚执行过 CREATE TABLE IF NOT EXISTS schema_version，
        // 表一定存在。这里兜底为单元测试/异常场景保留。
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
            return rs.getInt(1);
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
