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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite 数据源配置：
 * 1. 启动时创建 data/ 和 data/wsdl/ 目录
 * 2. 创建 DataSource bean（确保目录在连接前已存在）
 * 3. 设置 WAL 模式提高并发读写性能
 * 4. 执行 schema.sql 建表（IF NOT EXISTS 保证幂等）
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
     *
     * 这样保证所有后续依赖 JdbcTemplate 的 bean 在初始化时表已存在。
     */
    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        // 1. 创建数据目录
        createDataDirectories();

        // 2. 创建连接池
        DataSource ds = properties.initializeDataSourceBuilder().build();

        // 3. 用临时 JdbcTemplate 初始化数据库
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        enableWalMode(jdbcTemplate);
        executeSchemaSql(jdbcTemplate);
        executeMigrations(jdbcTemplate);

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
     * 执行数据库迁移：
     * 1. 为旧版数据库添加 description 列
     * 2. 将已有 REST 接口的响应数据迁移到 api_response 表
     * 3. 将已有 SOAP 接口的 operation 响应数据迁移到 api_response 表
     */
    private void executeMigrations(JdbcTemplate jdbcTemplate) {
        // 迁移 1：为旧版数据库添加 description 列
        addColumnIfNotExists(jdbcTemplate, "api_definition", "description", "TEXT");

        // 迁移 2：REST 接口数据迁移 —— 将旧的 responseCode/responseBody 迁移到 api_response 表
        try {
            int migratedRest = jdbcTemplate.update(
                    "INSERT INTO api_response (id, api_id, soap_operation_name, name, response_code, " +
                    "content_type, response_body, delay_ms, is_active, sort_order, created_at, updated_at) " +
                    "SELECT lower(hex(randomblob(16))), id, NULL, 'Default', response_code, " +
                    "content_type, response_body, delay_ms, 1, 0, created_at, updated_at " +
                    "FROM api_definition WHERE type = 'REST' " +
                    "AND id NOT IN (SELECT DISTINCT api_id FROM api_response WHERE soap_operation_name IS NULL)");
            if (migratedRest > 0) {
                log.info("REST 接口响应数据迁移完成，迁移 {} 条", migratedRest);
            }
        } catch (Exception e) {
            log.warn("REST 响应数据迁移跳过（可能已迁移或无数据）: {}", e.getMessage());
        }

        // 迁移 3：SOAP 接口数据迁移 —— 解析 soap_config JSON，为每个 operation 创建默认返回体
        migrateSoapResponses(jdbcTemplate);
    }

    /**
     * 为旧版数据库安全添加列，如果列已存在则忽略
     */
    private void addColumnIfNotExists(JdbcTemplate jdbcTemplate, String table, String column, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("数据库迁移：{} 表添加 {} 列成功", table, column);
        } catch (Exception e) {
            // SQLite 不支持 IF NOT EXISTS 语法，列已存在时会抛异常，安全忽略
            log.debug("列 {}.{} 已存在，跳过添加", table, column);
        }
    }

    /**
     * 将 SOAP 接口的 soapConfig 中各 operation 的响应数据迁移到 api_response 表
     */
    @SuppressWarnings("unchecked")
    private void migrateSoapResponses(JdbcTemplate jdbcTemplate) {
        try {
            // 查询所有 SOAP 接口
            List<Map<String, Object>> soapApis = jdbcTemplate.queryForList(
                    "SELECT id, soap_config, created_at, updated_at FROM api_definition WHERE type = 'SOAP' AND soap_config IS NOT NULL");

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

                        // 检查该 operation 是否已有返回体
                        Integer count = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM api_response WHERE api_id = ? AND soap_operation_name = ?",
                                Integer.class, apiId, operationName);
                        if (count != null && count > 0) {
                            continue;
                        }

                        // 从 operation 中提取响应数据
                        int responseCode = op.get("responseCode") != null ? ((Number) op.get("responseCode")).intValue() : 200;
                        int delayMs = op.get("delayMs") != null ? ((Number) op.get("delayMs")).intValue() : 0;
                        String responseBody = (String) op.get("responseBody");

                        jdbcTemplate.update(
                                "INSERT INTO api_response (id, api_id, soap_operation_name, name, response_code, " +
                                "content_type, response_body, delay_ms, is_active, sort_order, created_at, updated_at) " +
                                "VALUES (?, ?, ?, 'Default', ?, 'text/xml', ?, ?, 1, 0, ?, ?)",
                                UUID.randomUUID().toString(), apiId, operationName,
                                responseCode, responseBody, delayMs, createdAt, updatedAt);
                        migratedCount++;
                    }
                } catch (Exception e) {
                    log.warn("解析 SOAP 接口 {} 的 soapConfig 失败，跳过迁移: {}", apiId, e.getMessage());
                }
            }

            if (migratedCount > 0) {
                log.info("SOAP operation 响应数据迁移完成，迁移 {} 条", migratedCount);
            }
        } catch (Exception e) {
            log.warn("SOAP 响应数据迁移跳过: {}", e.getMessage());
        }
    }
}
