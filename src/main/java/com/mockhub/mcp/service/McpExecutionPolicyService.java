package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mcp.model.*;
import com.mockhub.mcp.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Credential snapshot ∩ credential current scope ∩ live owner capability ∩ current instance policy. */
@Service
public class McpExecutionPolicyService {
    private final McpTokenRepository tokens;
    private final McpScopeService owners;
    private final McpResourceScopeRepository resources;
    private final McpSettingsService settings;
    private final McpToolRegistry registry;
    private final ObjectMapper json;
    public McpExecutionPolicyService(McpTokenRepository tokens,McpScopeService owners,McpResourceScopeRepository resources,McpSettingsService settings,McpToolRegistry registry,ObjectMapper json){this.tokens=tokens;this.owners=owners;this.resources=resources;this.settings=settings;this.registry=registry;this.json=json;}
    public McpToken currentToken(){
        McpRequestContext context=McpRequestContext.current();
        if(context==null || !context.getOwnerId().equals(SecurityContextUtil.getCurrentUserId()))throw denied("缺少可信MCP凭证上下文");
        McpToken token=tokens.findById(context.getTokenId());
        if(!McpScopeCodec.isUsable(token) || !context.getOwnerId().equals(token.getUserId()))throw denied("MCP凭证已撤销、过期或范围无效");
        owners.requireActiveOwner(token.getUserId());return token;
    }
    public boolean canListTool(String name){
        if(!policyAllows(name))return false;
        return !effectiveTeams(scopeForTool(name)).isEmpty();
    }
    public List<String> effectiveTeams(McpScope scope){
        McpToken token=currentToken();Map<String,Set<McpScope>> current=McpScopeCodec.decode(token),snapshot=McpRequestContext.current().getTeamScopes();
        List<String> result=new ArrayList<String>();
        for(Map.Entry<String,Set<McpScope>> entry:current.entrySet())if(entry.getValue().contains(scope) && snapshot.containsKey(entry.getKey()) && snapshot.get(entry.getKey()).contains(scope) && owners.ownerAllows(token.getUserId(),entry.getKey(),scope))result.add(entry.getKey());
        return result;
    }
    public List<ObjectNode> assertTool(String name,JsonNode args){
        if(!policyAllows(name))throw denied("实例策略未允许此MCP工具");
        if(args!=null && !args.isMissingNode() && !args.isObject())throw new BizException(40001,"arguments必须为对象");
        ObjectNode safe=args!=null && args.isObject() ? ((ObjectNode)args).deepCopy() : json.createObjectNode();
        McpScope scope=scopeForTool(name);List<String> allowed=effectiveTeams(scope);
        String supplied=text(safe,"teamId"),actual=null;
        if(McpToolRegistry.GET_API_DETAIL.equals(name) || McpToolRegistry.UPDATE_API_DRAFT.equals(name))actual=resources.apiTeam(required(safe,"apiId"));
        else if(McpToolRegistry.EXPLAIN_MATCH.equals(name))actual=resources.logTeam(required(safe,"requestLogId"));
        else if(McpToolRegistry.APPLY_API_DRAFT.equals(name)){
            Map<String,Object> action=resources.action(required(safe,"actionId"));
            if(action==null || !currentToken().getUserId().equals(action.get("user_id")))throw denied("草稿不存在或不属于此凭证持有人");
            actual=(String)action.get("team_id");
        } else if(McpToolRegistry.SEARCH_APIS.equals(name) || McpToolRegistry.GET_API_STATS.equals(name)){
            if(!supplied.isEmpty())actual=supplied;
            else{
                if(allowed.isEmpty())throw denied("凭证在当前权限下没有可访问团队");
                List<ObjectNode> plan=new ArrayList<ObjectNode>();for(String team:allowed){ObjectNode item=safe.deepCopy();item.put("teamId",team);plan.add(item);}return plan;
            }
        }else actual=required(safe,"teamId");
        if(actual==null || actual.trim().isEmpty() || !allowed.contains(actual))throw denied("目标资源不在凭证当前有效团队/能力范围内");
        if(!supplied.isEmpty() && !supplied.equals(actual))throw denied("teamId与目标资源实际归属不一致");
        safe.put("teamId",actual);return Collections.singletonList(safe);
    }
    public boolean policyAllows(String name){McpSettings s=settings.load();return s.isEnabled() && registry.isEnabled(s,name) && (!McpToolRegistry.APPLY_API_DRAFT.equals(name) || McpSettings.MODE_DIRECT.equals(s.getWriteMode()));}
    public static McpScope scopeForTool(String name){
        if(McpToolRegistry.SEARCH_APIS.equals(name)||McpToolRegistry.GET_API_DETAIL.equals(name))return McpScope.API_READ;
        if(McpToolRegistry.GET_API_STATS.equals(name))return McpScope.STATS_READ;
        if(McpToolRegistry.GENERATE_RESPONSE.equals(name)||McpToolRegistry.GENERATE_CONDITIONS.equals(name)||McpToolRegistry.EXPLAIN_MATCH.equals(name))return McpScope.AI_GENERATE;
        if(McpToolRegistry.CREATE_API_DRAFT.equals(name))return McpScope.DRAFT_CREATE;
        if(McpToolRegistry.UPDATE_API_DRAFT.equals(name))return McpScope.DRAFT_UPDATE;
        if(McpToolRegistry.APPLY_API_DRAFT.equals(name))return McpScope.DRAFT_APPLY;
        throw denied("未知MCP工具");
    }
    private static String text(JsonNode args,String key){JsonNode value=args.get(key);return value!=null && value.isTextual()?value.asText().trim():"";}
    private static String required(JsonNode args,String key){String value=text(args,key);if(value.isEmpty())throw new BizException(40001,"缺少参数："+key);return value;}
    private static BizException denied(String message){return new BizException(40105,message);}
}
