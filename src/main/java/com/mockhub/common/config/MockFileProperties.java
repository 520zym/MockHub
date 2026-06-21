package com.mockhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mock 文件响应存储配置。
 */
@ConfigurationProperties(prefix = "mock.file")
public class MockFileProperties {

    /** 单个文件响应上传大小上限，默认 10MB。 */
    private long maxSizeBytes = 10L * 1024L * 1024L;

    /** 是否启用孤儿文件自动清理。 */
    private boolean orphanAutoCleanupEnabled = true;

    /** 自动清理时仅删除早于该小时数的孤儿文件，避免误删刚上传但未保存的文件。 */
    private int orphanRetentionHours = 24;

    /** 自动清理间隔，默认 1 小时。 */
    private long orphanCleanupIntervalMs = 60L * 60L * 1000L;

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public boolean isOrphanAutoCleanupEnabled() {
        return orphanAutoCleanupEnabled;
    }

    public void setOrphanAutoCleanupEnabled(boolean orphanAutoCleanupEnabled) {
        this.orphanAutoCleanupEnabled = orphanAutoCleanupEnabled;
    }

    public int getOrphanRetentionHours() {
        return orphanRetentionHours;
    }

    public void setOrphanRetentionHours(int orphanRetentionHours) {
        this.orphanRetentionHours = orphanRetentionHours;
    }

    public long getOrphanCleanupIntervalMs() {
        return orphanCleanupIntervalMs;
    }

    public void setOrphanCleanupIntervalMs(long orphanCleanupIntervalMs) {
        this.orphanCleanupIntervalMs = orphanCleanupIntervalMs;
    }
}
