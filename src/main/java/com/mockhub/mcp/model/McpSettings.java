package com.mockhub.mcp.model;

/**
 * MCP Server 全局配置（单行）。
 */
public class McpSettings {

    public static final String DEFAULT_ID = "default";
    public static final String MODE_DRAFT_ONLY = "DRAFT_ONLY";
    public static final String MODE_DIRECT = "DIRECT";

    private String id = DEFAULT_ID;
    private boolean enabled;
    private String writeMode = MODE_DRAFT_ONLY;

    private boolean toolSearchApis = true;
    private boolean toolGetApiDetail = true;
    private boolean toolGetApiStats = true;
    private boolean toolCreateApiDraft = true;
    private boolean toolApplyApiDraft;
    private boolean toolGenerateResponseBody = true;
    private boolean toolGenerateConditions = true;
    private boolean toolExplainMatchResult = true;

    private String updatedAt;
    private String updatedBy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWriteMode() { return writeMode; }
    public void setWriteMode(String writeMode) { this.writeMode = writeMode; }
    public boolean isToolSearchApis() { return toolSearchApis; }
    public void setToolSearchApis(boolean v) { this.toolSearchApis = v; }
    public boolean isToolGetApiDetail() { return toolGetApiDetail; }
    public void setToolGetApiDetail(boolean v) { this.toolGetApiDetail = v; }
    public boolean isToolGetApiStats() { return toolGetApiStats; }
    public void setToolGetApiStats(boolean v) { this.toolGetApiStats = v; }
    public boolean isToolCreateApiDraft() { return toolCreateApiDraft; }
    public void setToolCreateApiDraft(boolean v) { this.toolCreateApiDraft = v; }
    public boolean isToolApplyApiDraft() { return toolApplyApiDraft; }
    public void setToolApplyApiDraft(boolean v) { this.toolApplyApiDraft = v; }
    public boolean isToolGenerateResponseBody() { return toolGenerateResponseBody; }
    public void setToolGenerateResponseBody(boolean v) { this.toolGenerateResponseBody = v; }
    public boolean isToolGenerateConditions() { return toolGenerateConditions; }
    public void setToolGenerateConditions(boolean v) { this.toolGenerateConditions = v; }
    public boolean isToolExplainMatchResult() { return toolExplainMatchResult; }
    public void setToolExplainMatchResult(boolean v) { this.toolExplainMatchResult = v; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
