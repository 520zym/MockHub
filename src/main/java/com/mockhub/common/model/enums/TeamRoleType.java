package com.mockhub.common.model.enums;

/** 团队角色，不是全局账号身份。旧 MEMBER 只兼容为可编辑成员。 */
public enum TeamRoleType {
    VIEWER, EDITOR, TEAM_ADMIN;

    public static TeamRoleType fromStored(String role) {
        if ("MEMBER".equals(role)) return EDITOR;
        if (role == null) return null;
        try { return valueOf(role); } catch (IllegalArgumentException e) { return null; }
    }

    public boolean canWrite() { return this != VIEWER; }
    public boolean canAdminister() { return this == TEAM_ADMIN; }
}
