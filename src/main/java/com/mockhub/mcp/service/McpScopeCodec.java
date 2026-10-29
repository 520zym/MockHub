package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.mcp.model.McpScope;
import com.mockhub.mcp.model.McpToken;
import java.time.Instant;
import java.util.*;

/** Strict persisted scope codec. Missing, unknown or corrupt scope data never means all access. */
public final class McpScopeCodec {
    private static final ObjectMapper JSON = new ObjectMapper();
    private McpScopeCodec() { }
    public static Map<String,Set<McpScope>> decode(McpToken token) {
        if (token == null || token.getScopeVersion() != 1) throw invalid();
        try {
            JsonNode teams=JSON.readTree(token.getTeamIdsJson()), scopes=JSON.readTree(token.getScopesJson());
            if (teams == null || !teams.isArray() || teams.size() < 1 || teams.size() > 100 || scopes == null || !scopes.isObject() || scopes.size()!=teams.size()) throw invalid();
            Map<String,Set<McpScope>> result=new LinkedHashMap<String,Set<McpScope>>();
            for (JsonNode team:teams) {
                if (!team.isTextual() || team.asText().trim().isEmpty() || result.containsKey(team.asText())) throw invalid();
                JsonNode values=scopes.get(team.asText());
                if (values==null || !values.isArray() || values.size()<1) throw invalid();
                Set<McpScope> set=new LinkedHashSet<McpScope>();
                for (JsonNode value:values) {
                    if (!value.isTextual() || !set.add(McpScope.valueOf(value.asText()))) throw invalid();
                }
                result.put(team.asText(),Collections.unmodifiableSet(set));
            }
            return Collections.unmodifiableMap(result);
        } catch (BizException e) { throw e; } catch (Exception e) { throw invalid(); }
    }
    public static void encode(McpToken token, Map<String,Set<McpScope>> scopes) {
        try { token.setScopeVersion(1);token.setTeamIdsJson(JSON.writeValueAsString(scopes.keySet()));token.setScopesJson(JSON.writeValueAsString(scopes)); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode scopes",e); }
    }
    public static boolean isUsable(McpToken token) {
        if (token==null || token.getRevokedAt()!=null || token.getExpiresAt()==null) return false;
        try { if (!Instant.parse(token.getExpiresAt()).isAfter(Instant.now())) return false; decode(token); return true; }
        catch (Exception e) { return false; }
    }
    public static List<String> union(Map<String,Set<McpScope>> scopes) {
        Set<String> union=new LinkedHashSet<String>();for(Set<McpScope> values:scopes.values())for(McpScope scope:values)union.add(scope.name());
        return new ArrayList<String>(union);
    }
    public static Map<String,List<String>> strings(Map<String,Set<McpScope>> scopes) {
        Map<String,List<String>> result=new LinkedHashMap<String,List<String>>();
        for(Map.Entry<String,Set<McpScope>> entry:scopes.entrySet()){List<String> values=new ArrayList<String>();for(McpScope scope:entry.getValue())values.add(scope.name());result.put(entry.getKey(),values);}return result;
    }
    private static BizException invalid() { return new BizException(40105,"MCP凭证缺少有效显式范围，请重新签发"); }
}
