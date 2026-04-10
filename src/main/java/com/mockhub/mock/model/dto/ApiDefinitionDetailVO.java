package com.mockhub.mock.model.dto;

import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.model.entity.Tag;

import java.util.List;
import java.util.Map;

/**
 * 接口定义详情响应体
 * <p>
 * 用于编辑页面，包含完整信息：接口基本信息 + 所有返回体 + 标签列表。
 * SOAP 接口的返回体通过 soapOperationName 区分归属的 operation。
 */
public class ApiDefinitionDetailVO {

    private String id;
    private String teamId;
    private String groupId;
    private String type;
    private String name;
    private String description;
    private String method;
    private String path;

    /** 旧字段保留（兼容），实际使用 responses 中的数据 */
    private int responseCode;
    private String contentType;
    private String responseBody;
    private int delayMs;

    private boolean enabled;
    private String globalHeaderOverrides;
    private String soapConfig;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String updatedBy;

    /** 所有返回体列表（REST 的 soapOperationName 为 null，SOAP 按 operationName 区分） */
    private List<ApiResponse> responses;

    /** 关联标签列表 */
    private List<Tag> tags;

    public ApiDefinitionDetailVO() {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public List<ApiResponse> getResponses() {
        return responses;
    }

    public void setResponses(List<ApiResponse> responses) {
        this.responses = responses;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }
}
