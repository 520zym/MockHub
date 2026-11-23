package com.mockhub.system.model.dto;

/**
 * 创建团队请求体
 */
public class CreateTeamRequest {

    private String description;
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description=description; }

    private String initialAdminUserId;
    public String getInitialAdminUserId() { return initialAdminUserId; }
    public void setInitialAdminUserId(String initialAdminUserId) { this.initialAdminUserId=initialAdminUserId; }

    /** 团队名称 */
    private String name;

    /** 团队短标识（2~8 个大写字母/数字，全局唯一） */
    private String identifier;

    /** 团队颜色标识（HEX 格式） */
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
