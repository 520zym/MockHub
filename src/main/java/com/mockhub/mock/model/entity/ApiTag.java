package com.mockhub.mock.model.entity;

/**
 * 接口-标签关联实体
 * <p>
 * 对应数据库 api_tag 表（联合主键：apiId + tagId）。
 */
public class ApiTag {

    /** 接口 ID */
    private String apiId;

    /** 标签 ID */
    private String tagId;

    public ApiTag() {
    }

    public ApiTag(String apiId, String tagId) {
        this.apiId = apiId;
        this.tagId = tagId;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    @Override
    public String toString() {
        return "ApiTag{" +
                "apiId='" + apiId + '\'' +
                ", tagId='" + tagId + '\'' +
                '}';
    }
}
