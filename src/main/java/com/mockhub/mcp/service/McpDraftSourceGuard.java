package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.ai.model.AiPendingAction;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mcp.model.*;
import com.mockhub.mcp.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Runs under the pending repository's SQLite write transaction, before claim and aggregate mutation. */
@Service
public class McpDraftSourceGuard {
    private final McpTokenRepository tokens;
    private final McpScopeService owners;
    private final McpExecutionPolicyService execution;
    private final McpResourceScopeRepository resources;
    private final ObjectMapper json;
    public McpDraftSourceGuard(McpTokenRepository tokens,McpScopeService owners,McpExecutionPolicyService execution,McpResourceScopeRepository resources,ObjectMapper json){this.tokens=tokens;this.owners=owners;this.execution=execution;this.resources=resources;this.json=json;}
    public void bind(AiPendingAction action){
        McpToken token=execution.currentToken();McpRequestContext context=McpRequestContext.current();
        McpScope required=requiredScope(action);String team=action.getTeamId();
        ObjectNode args=json.createObjectNode().put("teamId",team);
        if(action.getTargetApiId()!=null)args.put("apiId",action.getTargetApiId());
        execution.assertTool(toolFor(required),args);
        Set<McpScope> snapshot=new LinkedHashSet<McpScope>(McpScopeCodec.decode(token).get(team));snapshot.retainAll(context.getTeamScopes().get(team));
        Map<String,Set<McpScope>> matrix=new LinkedHashMap<String,Set<McpScope>>();matrix.put(team,snapshot);
        ObjectNode record=json.createObjectNode();record.put("version",1);record.put("teamId",team);record.put("actionScope",required.name());
        if(action.getTargetApiId()!=null)record.put("targetApiId",action.getTargetApiId());else record.putNull("targetApiId");
        record.set("teamIds",json.valueToTree(Collections.singletonList(team)));record.set("teamScopes",json.valueToTree(matrix));
        action.setSourceType("MCP");action.setSourceTokenId(token.getId());action.setSourceScopeJson(record.toString());
    }
    public void verify(AiPendingAction action){
        if(!"MCP".equals(action.getSourceType()))throw denied("草稿来源未绑定，请重新生成");
        McpToken token=tokens.findById(action.getSourceTokenId());
        if(!McpScopeCodec.isUsable(token) || !action.getUserId().equals(token.getUserId()))throw denied("来源MCP凭证已撤销、过期或归属改变，请重新生成草稿");
        McpScope required=requiredScope(action);String team=action.getTeamId();
        Map<String,Set<McpScope>> snapshot;
        try{
            JsonNode record=json.readTree(action.getSourceScopeJson());
            if(record.path("version").asInt()!=1 || !team.equals(record.path("teamId").asText()) || !required.name().equals(record.path("actionScope").asText()) || !Objects.equals(action.getTargetApiId(),record.path("targetApiId").isNull()?null:record.path("targetApiId").asText()))throw denied("草稿来源快照与目标不一致");
            McpToken encoded=new McpToken();encoded.setScopeVersion(1);encoded.setTeamIdsJson(record.path("teamIds").toString());encoded.setScopesJson(record.path("teamScopes").toString());snapshot=McpScopeCodec.decode(encoded);
            JsonNode payload=json.readTree(action.getPayload());
            if(!team.equals(payload.path("teamId").asText()) || (payload.has("dto") && !team.equals(payload.path("dto").path("teamId").asText())))throw denied("草稿内容与来源团队不一致");
            if(action.getTargetApiId()!=null && !action.getTargetApiId().equals(payload.path("apiId").asText()))throw denied("草稿目标与来源快照不一致");
        }catch(BizException e){throw e;}catch(Exception e){throw denied("草稿来源快照无效，请重新生成");}
        Map<String,Set<McpScope>> current=McpScopeCodec.decode(token);
        if(!contains(snapshot,team,required) || !contains(current,team,required) || !owners.ownerAllows(token.getUserId(),team,required) || !execution.policyAllows(toolFor(required)))throw denied("来源凭证范围、持有人当前权限或实例工具策略已收缩，不能确认此草稿");
        if(action.getTargetApiId()!=null && !team.equals(resources.apiTeam(action.getTargetApiId())))throw denied("来源目标接口已删除或团队不一致");
        McpRequestContext caller=McpRequestContext.current();
        if(caller!=null){
            if(!token.getId().equals(caller.getTokenId()) || !contains(snapshot,team,McpScope.DRAFT_APPLY))throw denied("不能用另一凭证或新增的应用能力扩张旧草稿授权");
            execution.assertTool(McpToolRegistry.APPLY_API_DRAFT,json.createObjectNode().put("actionId",action.getId()).put("teamId",team));
        }
        // A real Web confirmation is explicit and may use DRAFT_ONLY, but never bypasses source revocation.
    }
    private static boolean contains(Map<String,Set<McpScope>> values,String team,McpScope scope){return values.containsKey(team)&&values.get(team).contains(scope);}
    private static McpScope requiredScope(AiPendingAction action){if("CREATE_API".equals(action.getActionType()))return McpScope.DRAFT_CREATE;if("UPDATE_API".equals(action.getActionType())||"UPDATE_API_FULL".equals(action.getActionType()))return McpScope.DRAFT_UPDATE;throw denied("未知草稿动作");}
    private static String toolFor(McpScope scope){return scope==McpScope.DRAFT_CREATE?McpToolRegistry.CREATE_API_DRAFT:McpToolRegistry.UPDATE_API_DRAFT;}
    private static BizException denied(String message){return new BizException(40926,message);}
}
