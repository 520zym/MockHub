package com.mockhub.mock.model.entity;

/**
 * 自定义动态变量分组实体
 * <p>
 * 对应数据库 custom_variable_group 表。同一变量下的候选值可以按分组划分，
 * 一个值可以同时属于多个分组（通过 custom_variable_group_value 关联表建立 M:N 关系）。
 * 占位符 {{varname.groupname}} 表示从指定分组随机挑选值。
 */
public class CustomVariableGroup {

    /** 分组 ID（UUID） */
    private String id;

    /** 所属变量 ID */
    private String variableId;

    /** 分组名，只允许 \w{1,32}，同一变量下唯一 */
    private String name;

    /** 分组描述，可选 */
    private String description;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVariableId() {
        return variableId;
    }

    public void setVariableId(String variableId) {
        this.variableId = variableId;
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
}
