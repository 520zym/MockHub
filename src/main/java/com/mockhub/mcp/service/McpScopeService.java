package com.mockhub.mcp.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.enums.TeamRoleType;
import com.mockhub.mcp.model.McpScope;
import com.mockhub.mcp.model.dto.McpTokenCreateRequest;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.*;

/** Owner facts are always read live. System-admin status never widens a credential's explicit teams. */
@Service
public class McpScopeService {
    private final UserRepository users;
    private final TeamRepository teams;
    public McpScopeService(UserRepository users,TeamRepository teams){this.users=users;this.teams=teams;}

    public Map<String,Set<McpScope>> authorizeMint(String ownerId,McpTokenCreateRequest request) {
        User owner=requireActiveOwner(ownerId);
        if(request.getTeamIds()==null || request.getTeamIds().isEmpty() || request.getTeamIds().size()>100 || request.getScopes()==null || request.getScopes().isEmpty())throw new BizException(40001,"必须显式选择1至100个团队与非空能力范围");
        Set<String> ids=new LinkedHashSet<String>();
        for(String id:request.getTeamIds())if(id==null || id.trim().isEmpty() || !ids.add(id))throw new BizException(40001,"团队ID无效或重复");
        Set<McpScope> global=parseScopes(request.getScopes());
        Map<String,List<String>> matrix=request.getTeamScopes();
        if(matrix!=null && !matrix.keySet().equals(ids))throw new BizException(40001,"按队范围必须精确覆盖所选团队");
        Map<String,Set<McpScope>> result=new LinkedHashMap<String,Set<McpScope>>();
        Set<McpScope> used=new LinkedHashSet<McpScope>();
        for(String teamId:ids){
            Set<McpScope> values=matrix==null ? new LinkedHashSet<McpScope>(global) : parseScopes(matrix.get(teamId));
            if(!global.containsAll(values))throw new BizException(40001,"团队能力必须是所选scope的子集");
            for(McpScope scope:values)if(!ownerAllows(owner,teamId,scope))throw new BizException(40104,"不能授予超出本人当前团队能力的MCP范围："+teamId+" / "+scope.name());
            used.addAll(values);result.put(teamId,values);
        }
        if(!used.equals(global))throw new BizException(40001,"scope并集必须与按队范围一致");
        return result;
    }
    public boolean ownerAllows(String ownerId,String teamId,McpScope scope){return ownerAllows(users.findById(ownerId),teamId,scope);}
    private boolean ownerAllows(User owner,String teamId,McpScope scope){
        if(owner==null || !owner.isActive())return false;
        Team team=teams.findById(teamId);if(team==null || (scope.requiresWrite() && team.isArchived()))return false;
        if("SUPER_ADMIN".equals(owner.getGlobalRole()))return true;
        if(!("USER".equals(owner.getGlobalRole()) || "MEMBER".equals(owner.getGlobalRole()) || "TEAM_ADMIN".equals(owner.getGlobalRole())))return false;
        TeamRoleType role=TeamRoleType.fromStored(users.findUserTeamRole(owner.getId(),teamId));
        return role!=null && (!scope.requiresWrite() || role.canWrite());
    }
    public User requireActiveOwner(String ownerId){User user=users.findById(ownerId);if(user==null || !user.isActive())throw new BizException(40101,"账号不存在或已停用");return user;}
    private Set<McpScope> parseScopes(List<String> values){
        if(values==null || values.isEmpty())throw new BizException(40001,"能力范围不能为空");
        Set<McpScope> result=new LinkedHashSet<McpScope>();
        for(String value:values){try{if(value==null || !result.add(McpScope.valueOf(value)))throw new IllegalArgumentException();}catch(Exception e){throw new BizException(40001,"未知或重复的MCP能力范围");}}return result;
    }
}
