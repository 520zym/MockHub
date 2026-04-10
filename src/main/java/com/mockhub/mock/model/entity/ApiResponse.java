package com.mockhub.mock.model.entity;

/**
 * 接口返回体实体
 * <p>
 * 对应数据库 api_response 表。每个接口定义可以有多个返回体，
 * 但同一 api_id + soap_operation_name 下只有一个 is_active=true 的生效返回体。
 * REST 接口的 soap_operation_name 为 null，SOAP 接口填对应的 operationName。
 */
public class ApiResponse {

    /** 返回体 ID（UUID） */
    private String id;

    /** 所属接口定义 ID */
    private String apiId;

    /** SOAP operation 名称，REST 为 null */
    private String soapOperationName;

    /** 返回体名称/标题（如"成功响应"、"404错误"） */
    private String name;

    /** HTTP 响应状态码 */
    private int responseCode;

    /** 响应内容类型 */
    private String contentType;

    /** 响应体内容，支持动态变量占位符 */
    private String responseBody;

    /** 延迟毫秒数 */
    private int delayMs;

    /** 是否为当前生效的返回体 */
    private boolean isActive;

    /** 排序序号 */
    private int sortOrder;

    /** 条件匹配规则 JSON，v2 预留，当前为 null */
    private String conditions;

    /** 创建时间（ISO 格式） */
    private String createdAt;

    /** 更新时间（ISO 格式） */
    private String updatedAt;

    public ApiResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "id='" + id + '\'' +
                ", apiId='" + apiId + '\'' +
                ", soapOperationName='" + soapOperationName + '\'' +
                ", name='" + name + '\'' +
                ", responseCode=" + responseCode +
                ", isActive=" + isActive +
                '}';
    }
}
