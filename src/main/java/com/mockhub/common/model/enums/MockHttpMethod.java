package com.mockhub.common.model.enums;

/**
 * Mock 接口支持的 HTTP 方法
 * 命名为 MockHttpMethod 避免与 Spring 的 HttpMethod 冲突
 */
public enum MockHttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH
}
