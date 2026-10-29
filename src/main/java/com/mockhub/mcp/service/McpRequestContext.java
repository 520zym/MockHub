package com.mockhub.mcp.service;

import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mcp.model.McpScope;
import com.mockhub.mcp.model.McpToken;
import java.util.Map;
import java.util.Set;

/** Only McpAuthService installs this typed object; JWT claims cannot fabricate it. */
public final class McpRequestContext {
    public static final String KEY="trustedMcpContext";
    private final String tokenId;
    private final String ownerId;
    private final Map<String,Set<McpScope>> teamScopes;
    McpRequestContext(McpToken token) { tokenId=token.getId();ownerId=token.getUserId();teamScopes=McpScopeCodec.decode(token); }
    public String getTokenId(){return tokenId;}
    public String getOwnerId(){return ownerId;}
    public Map<String,Set<McpScope>> getTeamScopes(){return teamScopes;}
    public static McpRequestContext current() {
        Object value=SecurityContextUtil.getUserInfo().get(KEY);
        return value instanceof McpRequestContext ? (McpRequestContext)value : null;
    }
}
