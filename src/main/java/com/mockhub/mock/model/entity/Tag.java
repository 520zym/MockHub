package com.mockhub.mock.model.entity;

/**
 * 标签实体
 * <p>
 * 对应数据库 tag 表。标签属于团队级别，用于给接口定义打标签。
 */
public class Tag {

    /** 标签 ID（UUID） */
    private String id;

    /** 所属团队 ID */
    private String teamId;

    /** 标签名称 */
    private String name;

    /** 标签颜色（HEX 格式） */
    private String color;

    public Tag() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return "Tag{" +
                "id='" + id + '\'' +
                ", teamId='" + teamId + '\'' +
                ", name='" + name + '\'' +
                ", color='" + color + '\'' +
                '}';
    }
}
