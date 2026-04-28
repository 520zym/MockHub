package com.mockhub.mock.model.dto;

import java.util.List;

/**
 * 批量操作接口的请求体。
 * <p>
 * action 取值：
 * <ul>
 *   <li>{@code enable}    — 批量启用</li>
 *   <li>{@code disable}   — 批量禁用</li>
 *   <li>{@code delete}    — 批量删除</li>
 *   <li>{@code move-group} — 批量移动到指定分组（targetGroupId 为空表示移到"未分组"）</li>
 * </ul>
 */
public class BatchApiRequest {

    /** 操作类型 */
    private String action;

    /** 待处理的接口 ID 列表 */
    private List<String> ids;

    /** move-group 时的目标分组 ID；null 或空表示置空（未分组） */
    private String targetGroupId;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public String getTargetGroupId() {
        return targetGroupId;
    }

    public void setTargetGroupId(String targetGroupId) {
        this.targetGroupId = targetGroupId;
    }
}
