package com.mockhub.mock.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 接口返回体创建/更新请求体
 * <p>
 * 用于前端提交多返回体数据，id 为 null 时表示新建。
 */
public class ApiResponseDTO {

    /** 返回体 ID，新建时为 null */
    private String id;

    /** SOAP operation 名称，REST 为 null */
    private String soapOperationName;

    /** 返回体名称/标题 */
    private String name;

    /** HTTP 响应状态码 */
    private int responseCode;

    /** 响应内容类型 */
    private String contentType;

    /** 响应体内容 */
    private String responseBody;

    /** 延迟毫秒数 */
    private int delayMs;

    /** 是否为当前生效的返回体 */
    private boolean isActive;

    /** 排序序号 */
    private int sortOrder;

    /**
     * 条件匹配规则 JSON 字符串，格式为 MatchRule 序列化结果。
     * null 或空对象表示无规则（兜底返回体）。
     */
    private String conditions;

    public ApiResponseDTO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSoapOperationName() {
        return soapOperationName;
    }

    public void setSoapOperationName(String soapOperationName) {
        this.soapOperationName = soapOperationName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        this.delayMs = delayMs;
    }

    public boolean isActive() {
        return isActive;
    }

    /**
     * Jackson 默认把 getter isActive() 映射成 JSON 字段 "active"，
     * 但前端历史上一直用 "isActive" 键名发送 → 该字段被 FAIL_ON_UNKNOWN_PROPERTIES=false 静默丢弃。
     * 旧版 dispatch 会回退到 api_definition 的 legacy 字段所以不显问题；v1.4.3 接入 matcher 后暴露。
     * 用 @JsonAlias 额外接受 "isActive" 作为反序列化键名，序列化仍为 "active" 保持向后兼容。
     */
    @JsonAlias("isActive")
    public void setActive(boolean active) {
        isActive = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }
}
