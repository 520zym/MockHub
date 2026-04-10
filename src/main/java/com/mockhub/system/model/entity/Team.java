package com.mockhub.system.model.entity;

/**
 * 团队实体
 * <p>
 * 对应数据库 team 表。
 * memberCount 和 apiCount 为非持久化字段，列表展示时通过关联查询填充。
 */
public class Team {

    /** 团队 ID（UUID） */
    private String id;

    /** 团队名称，全局唯一 */
    private String name;

    /** 团队短标识（2~8 个大写字母/数字），用于 Mock 路由路径 */
    private String identifier;

    /** 团队颜色标识（HEX 格式） */
    private String color;

    /** 创建时间（ISO 格式） */
    private String createdAt;

    // ========== 非持久化字段，列表展示用 ==========

    /** 团队成员数量（查询时填充） */
    private int memberCount;

    /** 团队接口数量（查询时填充） */
    private int apiCount;

    public Team() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public int getApiCount() {
        return apiCount;
    }

    public void setApiCount(int apiCount) {
        this.apiCount = apiCount;
    }

    @Override
    public String toString() {
        return "Team{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", identifier='" + identifier + '\'' +
                ", color='" + color + '\'' +
                '}';
    }
}
