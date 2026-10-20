package com.mockhub.mcp.model.dto;

/**
 * MCP 配置读取 / 保存结果。
 */
public class McpSettingsDTO {

    private boolean enabled;
    private String writeMode;
    private boolean toolSearchApis;
    private boolean toolGetApiDetail;
    private boolean toolGetApiStats;
    private boolean toolCreateApiDraft;
    private boolean toolApplyApiDraft;
    private boolean toolGenerateResponseBody;
    private boolean toolGenerateConditions;
    private boolean toolExplainMatchResult;
    private String updatedAt;

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
}
