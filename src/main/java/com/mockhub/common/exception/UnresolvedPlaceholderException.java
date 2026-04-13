package com.mockhub.common.exception;

/**
 * 动态变量占位符无法解析异常
 * <p>
 * 仅在"自定义变量存在但目标分组不存在或为空"场景下抛出，由 MockDispatchService
 * 捕获后返回 HTTP 500 + {code:50101, msg, data:null} 的统一错误响应。
 * <p>
 * 未知变量名（如用户拼写错误）不会抛此异常，而是保留原占位符，以保持与现有
 * {{path.xxx}} / 内置变量未命中时的行为一致。
 */
public class UnresolvedPlaceholderException extends RuntimeException {

    /** 所属团队短标识，便于错误信息定位 */
    private final String teamIdentifier;

    /** 原始占位符片段，如 {{airport.foo}} */
    private final String placeholder;

    /** 变量名，如 airport */
    private final String variableName;

    /** 分组名，如 foo（若是变量本身未命中则可能为 null） */
    private final String groupName;

    public UnresolvedPlaceholderException(String teamIdentifier, String placeholder,
                                          String variableName, String groupName) {
        super("Unresolved placeholder: " + placeholder + " (team '" + teamIdentifier +
                "', variable '" + variableName + "', group '" + groupName + "')");
        this.teamIdentifier = teamIdentifier;
        this.placeholder = placeholder;
        this.variableName = variableName;
        this.groupName = groupName;
    }

    public String getTeamIdentifier() {
        return teamIdentifier;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getGroupName() {
        return groupName;
    }
}
