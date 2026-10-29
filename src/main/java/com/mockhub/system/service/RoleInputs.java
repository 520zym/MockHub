package com.mockhub.system.service;

import com.mockhub.common.model.BizException;

/** Private adapter until the shared TeamRoleType lands. Never derive platform privileges from team roles. */
final class RoleInputs {
    private RoleInputs() { }
    static String teamRole(String role) {
        if ("MEMBER".equals(role)) return "EDITOR";
        if ("VIEWER".equals(role) || "EDITOR".equals(role) || "TEAM_ADMIN".equals(role)) return role;
        throw new BizException(40000, "未知团队角色");
    }
    static String globalRole(String role) {
        if ("MEMBER".equals(role) || "TEAM_ADMIN".equals(role)) return "USER";
        if ("USER".equals(role) || "SUPER_ADMIN".equals(role)) return role;
        throw new BizException(40000, "未知全局角色");
    }
}
