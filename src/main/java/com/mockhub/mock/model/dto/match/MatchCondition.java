package com.mockhub.mock.model.dto.match;

/**
 * 单条匹配条件。
 * <p>
 * 描述"从请求中取某个字段 → 应用某个操作符 → 与预期值比较"的一次判定。
 * 多条 MatchCondition 在 MatchRule 中以 AND 关系组合。
 *
 * <p>字段语义：
 * <ul>
 *   <li>source = BODY / QUERY，决定从请求体还是查询参数取值</li>
 *   <li>path：
 *     <ul>
 *       <li>source=BODY 时为点路径（如 user.addr.city），数组用 items[0].id</li>
 *       <li>source=QUERY 时为 key 名（如 userId）</li>
 *     </ul>
 *   </li>
 *   <li>operator：EQ / NE / CONTAINS / IS_EMPTY / IN / GT / GTE / LT / LTE / REGEX</li>
 *   <li>value：字符串存储；IN 为 JSON 数组字符串；IS_EMPTY 时忽略</li>
 *   <li>valueType：STRING / NUMBER / BOOLEAN，影响比较语义（数字比较走 BigDecimal）</li>
 * </ul>
 */
public class MatchCondition {

    /** 参数来源：BODY 或 QUERY */
    private String source;

    /** 字段路径（点路径或 key 名） */
    private String path;

    /** 操作符枚举 */
    private String operator;

    /** 预期值，字符串存储 */
    private String value;

    /** 值类型：STRING / NUMBER / BOOLEAN */
    private String valueType;

    public MatchCondition() {
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }
}
