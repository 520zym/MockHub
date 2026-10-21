package com.mockhub.mcp.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mcp.model.McpSettings;
import com.mockhub.mcp.model.dto.McpSettingsDTO;
import com.mockhub.mcp.model.dto.McpSettingsUpdateRequest;
import com.mockhub.mcp.repository.McpSettingsRepository;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * MCP 配置读取与工具开关。
 */
@Service
public class McpSettingsService {

    private final McpSettingsRepository repository;

    public McpSettingsService(McpSettingsRepository repository) {
        this.repository = repository;
    }

    public McpSettings load() {
        McpSettings settings = repository.load();
        if (settings == null) {
            settings = new McpSettings();
        }
        if (settings.getWriteMode() == null) {
            settings.setWriteMode(McpSettings.MODE_DRAFT_ONLY);
        }
        return settings;
    }

    public McpSettingsDTO getDto() {
        return toDto(load());
    }

    public McpSettingsDTO update(McpSettingsUpdateRequest request) {
        McpSettings settings = load();
        if (request.getWriteMode() != null) {
            String mode = request.getWriteMode().trim().toUpperCase();
            if (!McpSettings.MODE_DRAFT_ONLY.equals(mode) && !McpSettings.MODE_DIRECT.equals(mode)) {
                throw new BizException(40001, "不支持的 MCP 写操作策略");
            }
            settings.setWriteMode(mode);
        }
        settings.setEnabled(request.isEnabled());
        settings.setToolSearchApis(request.isToolSearchApis());
        settings.setToolGetApiDetail(request.isToolGetApiDetail());
        settings.setToolGetApiStats(request.isToolGetApiStats());
        settings.setToolCreateApiDraft(request.isToolCreateApiDraft());
        settings.setToolUpdateApiDraft(request.isToolUpdateApiDraft());
        settings.setToolApplyApiDraft(request.isToolApplyApiDraft());
        settings.setToolGenerateResponseBody(request.isToolGenerateResponseBody());
        settings.setToolGenerateConditions(request.isToolGenerateConditions());
        settings.setToolExplainMatchResult(request.isToolExplainMatchResult());
        settings.setUpdatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        settings.setUpdatedBy(SecurityContextUtil.getCurrentUsername());
        repository.save(settings);
        return toDto(settings);
    }

    public void assertEnabled() {
        if (!load().isEnabled()) {
            throw new BizException(40306, "MCP Server 未启用");
        }
    }

    public boolean isToolEnabled(String name) {
        McpSettings s = load();
        if ("mockhub.search_apis".equals(name)) return s.isToolSearchApis();
        if ("mockhub.get_api_detail".equals(name)) return s.isToolGetApiDetail();
        if ("mockhub.get_api_stats".equals(name)) return s.isToolGetApiStats();
        if ("mockhub.create_api_draft".equals(name)) return s.isToolCreateApiDraft();
        if ("mockhub.update_api_draft".equals(name)) return s.isToolUpdateApiDraft();
        if ("mockhub.apply_api_draft".equals(name)) return s.isToolApplyApiDraft();
        if ("mockhub.generate_response_body".equals(name)) return s.isToolGenerateResponseBody();
        if ("mockhub.generate_conditions".equals(name)) return s.isToolGenerateConditions();
        if ("mockhub.explain_match_result".equals(name)) return s.isToolExplainMatchResult();
        return false;
    }

    public String getWriteMode() {
        return load().getWriteMode();
    }

    private McpSettingsDTO toDto(McpSettings s) {
        McpSettingsDTO dto = new McpSettingsDTO();
        dto.setEnabled(s.isEnabled());
        dto.setWriteMode(s.getWriteMode());
        dto.setToolSearchApis(s.isToolSearchApis());
        dto.setToolGetApiDetail(s.isToolGetApiDetail());
        dto.setToolGetApiStats(s.isToolGetApiStats());
        dto.setToolCreateApiDraft(s.isToolCreateApiDraft());
        dto.setToolUpdateApiDraft(s.isToolUpdateApiDraft());
        dto.setToolApplyApiDraft(s.isToolApplyApiDraft());
        dto.setToolGenerateResponseBody(s.isToolGenerateResponseBody());
        dto.setToolGenerateConditions(s.isToolGenerateConditions());
        dto.setToolExplainMatchResult(s.isToolExplainMatchResult());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }
}