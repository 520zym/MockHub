package com.mockhub.mock.model.dto;

import com.mockhub.mock.model.entity.ApiDefinition;

import java.util.Map;

/**
 * Mock 分发时的路径匹配结果
 * <p>
 * 包含匹配到的接口定义和从 URL 中提取的路径参数。
 */
public class ApiMatchResult {

    /** 匹配到的接口定义 */
    private ApiDefinition api;

    /** 路径参数，如 {"id": "123"}，精确匹配时为空 Map */
    private Map<String, String> pathVariables;

    public ApiMatchResult() {
    }

    public ApiMatchResult(ApiDefinition api, Map<String, String> pathVariables) {
        this.api = api;
        this.pathVariables = pathVariables;
    }

    public ApiDefinition getApi() {
        return api;
    }

    public void setApi(ApiDefinition api) {
        this.api = api;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }

    public void setPathVariables(Map<String, String> pathVariables) {
        this.pathVariables = pathVariables;
    }
}
