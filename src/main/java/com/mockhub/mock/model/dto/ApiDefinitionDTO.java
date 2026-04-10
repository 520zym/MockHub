package com.mockhub.mock.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 接口定义创建/更新请求体
 */
public class ApiDefinitionDTO {

    /** 所属团队 ID */
    private String teamId;

    /** 所属分组 ID，可为 null */
    private String groupId;

    /** 接口类型：REST 或 SOAP */
    private String type;

    /** 接口名称 */
    private String name;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String path;

    /** 响应状态码 */
    private int responseCode;

    /** 响应内容类型 */
    private String contentType;

    /** 响应体 */
    private String responseBody;

    /** 延迟毫秒数 */
    private int delayMs;

    /** 是否启用 */
    private boolean enabled;

    /** 标签 ID 列表 */
    private List<String> tagIds;

    /** 全局响应头覆盖 */
    private Map<String, String> globalHeaderOverrides;

    /** SOAP 配置（type=SOAP 时使用），直接传对象而非 JSON 字符串 */
    private Object soapConfig;

    public ApiDefinitionDTO() {
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

    public List<String> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<String> tagIds) {
        this.tagIds = tagIds;
    }

    public Map<String, String> getGlobalHeaderOverrides() {
        return globalHeaderOverrides;
    }

    public void setGlobalHeaderOverrides(Map<String, String> globalHeaderOverrides) {
        this.globalHeaderOverrides = globalHeaderOverrides;
    }

    public Object getSoapConfig() {
        return soapConfig;
    }

    public void setSoapConfig(Object soapConfig) {
        this.soapConfig = soapConfig;
    }
}
