package com.mockhub.mock.model.dto;

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

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
