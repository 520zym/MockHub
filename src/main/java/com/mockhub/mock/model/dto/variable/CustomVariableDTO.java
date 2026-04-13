package com.mockhub.mock.model.dto.variable;

import java.util.List;

/**
 * 自定义动态变量聚合视图 DTO
 * <p>
 * 包含变量主信息、全部候选值、全部分组（每个分组带其成员的值 ID 列表）。
 * 主要用于 GET /api/teams/{teamId}/variables 返回给前端一次性展示维护页。
 */
public class CustomVariableDTO {

    /** 变量 ID */
    private String id;

    /** 所属团队 ID */
    private String teamId;

    /** 变量名 */
    private String name;

    /** 变量描述 */
    private String description;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 全部候选值 */
    private List<ValueView> values;

    /** 全部分组 */
    private List<GroupView> groups;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<ValueView> getValues() { return values; }
    public void setValues(List<ValueView> values) { this.values = values; }

    public List<GroupView> getGroups() { return groups; }
    public void setGroups(List<GroupView> groups) { this.groups = groups; }

    /**
     * 候选值视图
     */
    public static class ValueView {
        private String id;
        private String value;
        private String description;
        private int sortOrder;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }

    /**
     * 分组视图，含其包含的值 ID 列表
     */
    public static class GroupView {
        private String id;
        private String name;
        private String description;
        private List<String> valueIds;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getValueIds() { return valueIds; }
        public void setValueIds(List<String> valueIds) { this.valueIds = valueIds; }
    }
}
