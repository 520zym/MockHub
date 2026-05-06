package com.mockhub.stats.model.dto;

/**
 * 接口热度 TOP-N 列表项。
 * <p>
 * 基于 request_log 在窗口内按 api_id 聚合统计。如对应接口已被删除，
 * apiName / method / path 等字段可能为 null（LEFT JOIN 结果），但 callCount 仍准确。
 */
public class TopApiVO {

    /** 接口 ID（可用于跳转编辑） */
    private String apiId;

    /** 接口所属团队 ID */
    private String teamId;

    /** 团队短标识（用于全部团队视图下展示来源团队） */
    private String teamIdentifier;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String path;

    /** 接口名称 */
    private String name;

    /** 窗口内调用次数 */
    private long callCount;

    public TopApiVO() {
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTeamIdentifier() {
        return teamIdentifier;
    }

    public void setTeamIdentifier(String teamIdentifier) {
        this.teamIdentifier = teamIdentifier;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCallCount() {
        return callCount;
    }

    public void setCallCount(long callCount) {
        this.callCount = callCount;
    }
}
