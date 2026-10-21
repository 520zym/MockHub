package com.mockhub.ai.model.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前登录用户可见的 AI 状态：是否启用、是否可用、可用能力。
 * <p>
 * 前端据此决定是否渲染悬浮入口、生成按钮与诊断入口。
 */
public class AiStatusDTO {

    private boolean enabled;
    /** 入口是否可见：已启用且当前用户在使用范围内 */
    private boolean visible;
    private boolean available;
    private String reason;
    private String provider;
    private String model;
    private Map<String, Boolean> capabilities = new LinkedHashMap<String, Boolean>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Map<String, Boolean> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Boolean> capabilities) {
        this.capabilities = capabilities;
    }
}