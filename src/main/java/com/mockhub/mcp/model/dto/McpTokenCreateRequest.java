package com.mockhub.mcp.model.dto;

/**
 * 创建 Token 请求。expiresInDays=0 表示永不过期。
 */
public class McpTokenCreateRequest {

    private String name;
    private int expiresInDays = 30;
    private java.util.List<String> teamIds;
    private java.util.List<String> scopes;
    private java.util.Map<String,java.util.List<String>> teamScopes;
    public java.util.List<String> getTeamIds(){return teamIds;}
    public void setTeamIds(java.util.List<String> value){teamIds=value;}
    public java.util.List<String> getScopes(){return scopes;}
    public void setScopes(java.util.List<String> value){scopes=value;}
    public java.util.Map<String,java.util.List<String>> getTeamScopes(){return teamScopes;}
    public void setTeamScopes(java.util.Map<String,java.util.List<String>> value){teamScopes=value;}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getExpiresInDays() { return expiresInDays; }
    public void setExpiresInDays(int expiresInDays) { this.expiresInDays = expiresInDays; }
}
