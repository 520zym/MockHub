package com.mockhub.system.model.dto;

/**
 * 修改团队请求体（字段可选更新）
 */
public class UpdateTeamRequest {

    /** 团队名称 */
    private String name;

    /** 团队短标识 */
    private String identifier;

    /** 团队颜色标识 */
    private String color;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
