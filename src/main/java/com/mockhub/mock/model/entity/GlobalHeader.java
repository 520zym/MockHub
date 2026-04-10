package com.mockhub.mock.model.entity;

/**
 * 全局响应头实体
 * <p>
 * 对应数据库 global_header 表。
 * 团队级别的全局响应头，每个 Mock 响应自动附加。
 * headerValue 支持动态变量（如 {{uuid}}、{{timestamp}}）。
 */
public class GlobalHeader {

    /** 全局响应头 ID（UUID） */
    private String id;

    /** 所属团队 ID */
    private String teamId;

    /** 响应头名称 */
    private String headerName;

    /** 响应头值，支持动态变量占位符 */
    private String headerValue;

    /** 是否启用 */
    private boolean enabled;

    /** 排序序号 */
    private int sortOrder;

    public GlobalHeader() {
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

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderValue() {
        return headerValue;
    }

    public void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return "GlobalHeader{" +
                "id='" + id + '\'' +
                ", teamId='" + teamId + '\'' +
                ", headerName='" + headerName + '\'' +
                ", enabled=" + enabled +
                ", sortOrder=" + sortOrder +
                '}';
    }
}
