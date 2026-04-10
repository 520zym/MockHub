package com.mockhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志保留策略配置属性
 */
@ConfigurationProperties(prefix = "log.retain")
public class LogRetainProperties {

    /** 日志保留模式：count（按条数）或 days（按天数） */
    private String mode = "count";

    /** mode=count 时，保留最新 N 条 */
    private int count = 1000;

    /** mode=days 时，保留最近 N 天 */
    private int days = 30;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }
}
