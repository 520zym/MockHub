package com.mockhub.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据库当前身份快照。只包含授权所需字段，不携带密码或 JWT。
 */
public final class CurrentUser {

    private final String userId;
    private final String username;
    private final String globalRole;
    private final List<String> teamIds;

    public CurrentUser(String userId, String username, String globalRole, List<String> teamIds) {
        this.userId = userId;
        this.username = username;
        this.globalRole = globalRole;
        this.teamIds = Collections.unmodifiableList(new ArrayList<String>(teamIds));
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getGlobalRole() {
        return globalRole;
    }

    public List<String> getTeamIds() {
        return teamIds;
    }
}
