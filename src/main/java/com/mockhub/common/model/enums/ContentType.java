package com.mockhub.common.model.enums;

/**
 * 响应内容类型
 */
public enum ContentType {
    JSON("application/json"),
    XML("application/xml"),
    TEXT("text/plain");

    private final String value;

    ContentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
