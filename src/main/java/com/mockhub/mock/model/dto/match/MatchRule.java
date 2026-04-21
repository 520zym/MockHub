package com.mockhub.mock.model.dto.match;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * 响应匹配规则：一组 AND 关系的 MatchCondition。
 * <p>
 * 持久化时序列化为 JSON 存入 api_response.conditions 字段。
 * conditions 为 null 或空列表视为"无规则"——作为兜底返回体。
 */
public class MatchRule {

    /** 条件列表，AND 关系 */
    private List<MatchCondition> conditions;

    public MatchRule() {
    }

    public List<MatchCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<MatchCondition> conditions) {
        this.conditions = conditions;
    }

    /**
     * 判断规则是否为"空"——conditions 为 null 或长度为 0。
     * 空规则对应"无规则"返回体，作为兜底。
     *
     * <p>标记 @JsonIgnore 避免 Jackson 把它当 getter 序列化成 "empty" 字段，
     * 否则反序列化时会因找不到 setter 而抛 UnrecognizedPropertyException。
     *
     * @return true 表示无规则
     */
    @JsonIgnore
    public boolean isEmpty() {
        return conditions == null || conditions.isEmpty();
    }
}
