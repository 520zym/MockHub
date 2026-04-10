package com.mockhub.log.model;

/**
 * 操作日志实体
 * <p>
 * 记录用户对接口、分组、标签、团队等资源的增删改操作。
 * 对应数据库表 operation_log。
 */
public class OperationLog {

    /** 主键 UUID */
    private String id;

    /** 团队 ID，可为 null（如系统级操作） */
    private String teamId;

    /** 操作用户 ID */
    private String userId;

    /** 操作用户名（冗余存储，避免关联查询） */
    private String username;

    /** 操作类型：CREATE / UPDATE / DELETE / TOGGLE / IMPORT */
    private String action;

    /** 目标类型：API / GROUP / TAG / TEAM / USER 等 */
    private String targetType;

    /** 目标资源 ID */
    private String targetId;

    /** 目标资源名称（冗余存储） */
    private String targetName;

    /** 操作详情描述 */
    private String detail;

    /** 创建时间，ISO 格式 */
    private String createdAt;

    public OperationLog() {
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
