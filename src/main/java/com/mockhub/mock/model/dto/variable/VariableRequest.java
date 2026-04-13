package com.mockhub.mock.model.dto.variable;

/**
 * 创建/更新变量的通用请求体
 * <p>
 * 同时用于 POST（创建）和 PUT（更新）。字段校验在 Service 层完成（变量名正则、保留字等）。
 */
public class VariableRequest {

    /** 变量名，必填，只允许 \w{1,32}，不允许与内置变量重名 */
    private String name;

    /** 变量描述，可选 */
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
