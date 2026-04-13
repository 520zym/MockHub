package com.mockhub.mock.model.entity;

/**
 * 自定义动态变量候选值实体
 * <p>
 * 对应数据库 custom_variable_value 表。一个变量下可以有多个候选值，
 * Mock 响应时从候选值集合中随机挑选一个返回。一个值可以属于多个分组（M:N 关联）。
 * description 为人类可读描述（如机场四码对应的机场全称），不参与替换输出。
 */
public class CustomVariableValue {

    /** 值 ID（UUID） */
    private String id;

    /** 所属变量 ID */
    private String variableId;

    /** 实际值，长度 1~256 */
    private String value;

    /** 人类可读描述，可选，不参与替换输出 */
    private String description;

    /** 排序序号（同一变量内按此升序排列） */
    private int sortOrder;

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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
