package com.mockhub.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * SQLite 数据源配置：
 * 1. 启动时创建 data/ 和 data/wsdl/ 目录
 * 2. 设置 WAL 模式提高并发读写性能
 * 3. 执行 schema.sql 建表（IF NOT EXISTS 保证幂等）
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataProperties dataProperties;

    @PostConstruct
    public void init() {
        createDataDirectories();
        enableWalMode();
        executeSchemaSql();
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
    private void enableWalMode() {
        jdbcTemplate.execute("PRAGMA journal_mode=WAL;");
        log.info("SQLite WAL 模式已启用");
    }

    /**
     * 读取 classpath:schema.sql 并执行建表语句
     */
    private void executeSchemaSql() {
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            InputStream inputStream = resource.getInputStream();
            String schemaSql = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            inputStream.close();

            // 按分号拆分并逐条执行（SQLite JdbcTemplate 不支持多条语句一次执行）
            String[] statements = schemaSql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
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
