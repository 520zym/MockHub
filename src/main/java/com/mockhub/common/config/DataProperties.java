package com.mockhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据目录配置属性
 */
@ConfigurationProperties(prefix = "data")
public class DataProperties {

    /**
     * 数据目录路径，存放 SQLite 数据库和 WSDL 文件等
     */
    private String path = "./data";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
