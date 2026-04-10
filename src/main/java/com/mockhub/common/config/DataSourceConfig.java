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

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
}
