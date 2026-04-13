package com.mockhub.mock.model.dto.variable;

/**
 * 单条候选值的请求体（新增/编辑复用），也用作批量入参的数组元素
 */
public class ValueRequest {

    /** 实际值，必填，长度 1~256 */
    private String value;

    /** 人类可读描述，可选，不参与替换输出 */
    private String description;

    /** 排序序号，可选，未传时 Service 自动分配 */
    private Integer sortOrder;

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
