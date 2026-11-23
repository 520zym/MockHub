package com.mockhub.mcp.model.dto;

/**
 * 创建 Token 的结果：token 字段为明文，仅此一次返回。
 */
public class McpTokenCreateResult {

    private String id;
    private String name;
    private String token;
    private String tokenPrefix;
    private String expiresAt;
    private java.util.List<String> teamIds;
    private java.util.List<String> scopes;
    private java.util.Map<String,java.util.List<String>> teamScopes;
    public java.util.List<String> getTeamIds(){return teamIds;}
    public void setTeamIds(java.util.List<String> value){teamIds=value;}
    public java.util.List<String> getScopes(){return scopes;}
    public void setScopes(java.util.List<String> value){scopes=value;}
    public java.util.Map<String,java.util.List<String>> getTeamScopes(){return teamScopes;}
    public void setTeamScopes(java.util.Map<String,java.util.List<String>> value){teamScopes=value;}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
