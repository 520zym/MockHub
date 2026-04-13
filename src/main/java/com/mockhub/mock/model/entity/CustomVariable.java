package com.mockhub.mock.model.entity;

/**
 * 自定义动态变量实体
 * <p>
 * 对应数据库 custom_variable 表。每个团队可以维护多个自定义变量，
 * 变量名在团队内唯一，不允许与内置变量（timestamp/uuid/date/datetime/random_int/path）重名。
 * 变量下挂载若干候选值（CustomVariableValue）和若干分组（CustomVariableGroup）。
 */
public class CustomVariable {

    /** 变量 ID（UUID） */
    private String id;

    /** 所属团队 ID */
    private String teamId;

    /** 变量名，只允许 \w{1,32}，团队内唯一，不允许与内置变量重名 */
    private String name;

    /** 变量描述，可选 */
    private String description;

    /** 创建时间（ISO-8601 字符串） */
    private String createdAt;

    /** 更新时间（ISO-8601 字符串） */
    private String updatedAt;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}
