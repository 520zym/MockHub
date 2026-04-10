package com.mockhub.system.model.dto;

import java.util.List;

/**
 * 为用户分配团队请求体（整体替换）
 */
public class AssignTeamsRequest {

    /** 团队角色列表 */
    private List<TeamRoleItem> teamRoles;

    public List<TeamRoleItem> getTeamRoles() {
        return teamRoles;
    }

    public void setTeamRoles(List<TeamRoleItem> teamRoles) {
        this.teamRoles = teamRoles;
    }

    /**
     * 单条团队角色分配项
     */
    public static class TeamRoleItem {

        /** 团队 ID */
        private String teamId;

        /** 团队内角色：TEAM_ADMIN / MEMBER */
        private String role;

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
