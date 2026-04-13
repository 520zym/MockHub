package com.mockhub.mock.model.dto.variable;

import java.util.List;

/**
 * 创建/更新分组的请求体
 * <p>
 * 新建时 valueIds 可选（可以先建空分组，再编辑往里加值）。
 * 编辑时 valueIds 若非 null 则全量替换（先删旧关联再插新关联）。
 */
public class GroupRequest {

    /** 分组名，必填，\w{1,32}，同一变量内唯一 */
    private String name;

    /** 分组描述，可选 */
    private String description;

    /** 分组成员的候选值 ID 列表 */
    private List<String> valueIds;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getValueIds() { return valueIds; }
    public void setValueIds(List<String> valueIds) { this.valueIds = valueIds; }
}
