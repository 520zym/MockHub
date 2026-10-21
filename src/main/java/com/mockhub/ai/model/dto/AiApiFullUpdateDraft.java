package com.mockhub.ai.model.dto;

import com.mockhub.mock.model.dto.ApiDefinitionDTO;

/** MCP 修改已有接口（含多返回体）的待确认草稿。 */
public class AiApiFullUpdateDraft {

    private String apiId;
    private String teamId;
    private ApiDefinitionDTO dto;

    public String getApiId() { return apiId; }
    public void setApiId(String apiId) { this.apiId = apiId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public ApiDefinitionDTO getDto() { return dto; }
    public void setDto(ApiDefinitionDTO dto) { this.dto = dto; }
}
