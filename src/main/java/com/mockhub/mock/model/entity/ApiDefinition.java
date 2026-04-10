package com.mockhub.mock.model.entity;

/**
 * 接口定义实体
 * <p>
 * 对应数据库 api_definition 表。
 * soapConfig 和 globalHeaderOverrides 在数据库中以 JSON 字符串存储，
 * 运行时通过 Jackson ObjectMapper 进行序列化/反序列化。
 */
public class ApiDefinition {

    /** 接口 ID（UUID） */
    private String id;

    /** 所属团队 ID */
    private String teamId;

    /** 所属分组 ID，可为 null（未分组） */
    private String groupId;

    /** 接口类型：REST 或 SOAP */
    private String type;

    /** 接口名称 */
    private String name;

    /** HTTP 方法：GET / POST / PUT / DELETE / PATCH */
    private String method;

    /** 请求路径（不含团队标识前缀），支持 {xxx} 路径参数 */
    private String path;

    /** 响应状态码 */
    private int responseCode;

    /** 响应内容类型 */
    private String contentType;

    /** 响应体，可能很大（5~6MB），支持动态变量占位符 */
    private String responseBody;

    /** 延迟毫秒数 */
    private int delayMs;

    /** 是否启用 */
    private boolean enabled;

    /** 全局响应头覆盖，JSON 字符串存储 */
    private String globalHeaderOverrides;

    /** SOAP 配置，JSON 字符串存储，type=SOAP 时使用 */
    private String soapConfig;

    /** 多场景响应配置，v1 为 null，v2 预留 */
    private String scenarios;

    /** 创建人 ID */
    private String createdBy;

    /** 创建时间（ISO 格式） */
    private String createdAt;

    /** 更新时间（ISO 格式） */
    private String updatedAt;

    /** 更新人 ID */
    private String updatedBy;

    public ApiDefinition() {
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getGlobalHeaderOverrides() {
        return globalHeaderOverrides;
    }

    public void setGlobalHeaderOverrides(String globalHeaderOverrides) {
        this.globalHeaderOverrides = globalHeaderOverrides;
    }

    public String getSoapConfig() {
        return soapConfig;
    }

    public void setSoapConfig(String soapConfig) {
        this.soapConfig = soapConfig;
    }

    public String getScenarios() {
        return scenarios;
    }

    public void setScenarios(String scenarios) {
        this.scenarios = scenarios;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public String toString() {
        return "ApiDefinition{" +
                "id='" + id + '\'' +
                ", teamId='" + teamId + '\'' +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
