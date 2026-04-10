package com.mockhub.mock.model.entity;

/**
 * 接口分组实体
 * <p>
 * 对应数据库 api_group 表。
 * apiCount 为非持久化字段，列表展示时通过关联查询填充。
 */
public class ApiGroup {

    /** 分组 ID（UUID） */
    private String id;

    /** 所属团队 ID */
    private String teamId;

    /** 分组名称 */
    private String name;

    /** 排序序号 */
    private int sortOrder;

    /** 创建时间（ISO 格式） */
    private String createdAt;

    // ========== 非持久化字段 ==========

    /** 分组下接口数量（查询时填充） */
    private int apiCount;

    public ApiGroup() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getApiCount() {
        return apiCount;
    }

    public void setApiCount(int apiCount) {
        this.apiCount = apiCount;
    }

    @Override
    public String toString() {
        return "ApiGroup{" +
                "id='" + id + '\'' +
                ", teamId='" + teamId + '\'' +
                ", name='" + name + '\'' +
                ", sortOrder=" + sortOrder +
                '}';
    }
}
