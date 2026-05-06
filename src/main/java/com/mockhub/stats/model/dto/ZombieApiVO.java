package com.mockhub.stats.model.dto;

/**
 * 僵尸接口列表项。
 * <p>
 * 选取条件：last_called_at 早于阈值（默认 30 天前）或为 null（从未被调用过）。
 * 与 TopApiVO 不同的是，此数据基于 api_definition.last_called_at 而非 request_log，
 * 所以不受日志清理影响。
 */
public class ZombieApiVO {

    /** 接口 ID */
    private String apiId;

    /** 接口所属团队 ID */
    private String teamId;

    /** 团队短标识 */
    private String teamIdentifier;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String path;

    /** 接口名称 */
    private String name;

    /** 最近一次调用时间（ISO 格式），从未调用则为 null */
    private String lastCalledAt;

    /** 累计命中次数 */
    private long hitCount;

    public ZombieApiVO() {
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

    public String getLastCalledAt() {
        return lastCalledAt;
    }

    public void setLastCalledAt(String lastCalledAt) {
        this.lastCalledAt = lastCalledAt;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }
}
