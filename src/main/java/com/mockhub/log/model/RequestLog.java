package com.mockhub.log.model;

/**
 * 请求日志实体
 * <p>
 * 记录每次 Mock 请求的详细信息，包括请求头、请求体、请求参数等。
 * 对应数据库表 request_log。requestHeaders、requestBody、requestParams 均以 JSON 字符串形式存储。
 */
public class RequestLog {

    /** 主键 UUID */
    private String id;

    /** 团队 ID */
    private String teamId;

    /** 匹配到的接口定义 ID */
    private String apiId;

    /** 请求路径（不含 /mock/{identifier} 前缀） */
    private String apiPath;

    /** HTTP 方法：GET / POST / PUT / DELETE / PATCH */
    private String method;

    /** 请求头，JSON 字符串 */
    private String requestHeaders;

    /** 请求体，JSON 字符串 */
    private String requestBody;

    /** 请求参数（query string），JSON 字符串 */
    private String requestParams;

    /** 响应状态码 */
    private int responseCode;

    /** 响应耗时（毫秒） */
    private long durationMs;

    /** 创建时间，ISO 格式 */
    private String createdAt;

    public RequestLog() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getRequestParams() {
        return requestParams;
    }

    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
