package com.mockhub.system.model.dto;

/**
 * 全局设置 DTO
 */
public class SettingsDTO {

    /** 日志保留模式：count / days */
    private String logRetainMode;

    /** 保留条数（count 模式） */
    private int logRetainCount;

    /** 保留天数（days 模式） */
    private int logRetainDays;

    /** 是否启用 Mock 接口的 CORS 支持 */
    private boolean mockCorsEnabled;

    /** 服务器地址（用于拼接 Mock URL），为空时自动检测内网 IP */
    private String serverAddress;

    public String getLogRetainMode() {
        return logRetainMode;
    }

    public void setLogRetainMode(String logRetainMode) {
        this.logRetainMode = logRetainMode;
    }

    public int getLogRetainCount() {
        return logRetainCount;
    }

    public void setLogRetainCount(int logRetainCount) {
        this.logRetainCount = logRetainCount;
    }

    public int getLogRetainDays() {
        return logRetainDays;
    }

    public void setLogRetainDays(int logRetainDays) {
        this.logRetainDays = logRetainDays;
    }

    public boolean isMockCorsEnabled() {
        return mockCorsEnabled;
    }

    public void setMockCorsEnabled(boolean mockCorsEnabled) {
        this.mockCorsEnabled = mockCorsEnabled;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }
}
