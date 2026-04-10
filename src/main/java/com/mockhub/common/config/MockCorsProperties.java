package com.mockhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mock CORS 配置属性
 */
@ConfigurationProperties(prefix = "mock.cors")
public class MockCorsProperties {

    /** 是否为 /mock/** 路径启用 CORS 跨域支持 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
