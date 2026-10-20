package com.mockhub.mcp.controller;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.Result;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.service.LogService;
import com.mockhub.mcp.model.dto.McpSettingsDTO;
import com.mockhub.mcp.model.dto.McpSettingsUpdateRequest;
import com.mockhub.mcp.model.dto.McpTokenCreateRequest;
import com.mockhub.mcp.model.dto.McpTokenCreateResult;
import com.mockhub.mcp.model.dto.McpTokenVO;
import com.mockhub.mcp.service.McpSettingsService;
import com.mockhub.mcp.service.McpTokenService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * MCP 配置与 Token 管理，仅超级管理员可用。
 */
@RestController
@RequestMapping("/api/mcp")
public class McpAdminController {

    private final McpSettingsService settingsService;
    private final McpTokenService tokenService;
    private final LogService logService;

    public McpAdminController(McpSettingsService settingsService,
                              McpTokenService tokenService,
                              LogService logService) {
        this.settingsService = settingsService;
        this.tokenService = tokenService;
        this.logService = logService;
    }

    @GetMapping("/settings")
    public Result<McpSettingsDTO> getSettings() {
        checkSuperAdmin();
        return Result.ok(settingsService.getDto());
    }

    @PutMapping("/settings")
    public Result<McpSettingsDTO> saveSettings(@RequestBody McpSettingsUpdateRequest request) {
        checkSuperAdmin();
        McpSettingsDTO dto = settingsService.update(request);
        record("UPDATE", "更新 MCP 配置：enabled=" + dto.isEnabled() + ", writeMode=" + dto.getWriteMode());
        return Result.ok(dto);
    }

    @GetMapping("/tokens")
    public Result<List<McpTokenVO>> listTokens() {
        checkSuperAdmin();
        return Result.ok(tokenService.list());
    }

    @PostMapping("/tokens")
    public Result<McpTokenCreateResult> createToken(@RequestBody McpTokenCreateRequest request) {
        checkSuperAdmin();
        McpTokenCreateResult result = tokenService.create(SecurityContextUtil.getCurrentUserId(), request);
        record("CREATE", "创建 MCP Token：" + result.getTokenPrefix() + "…");
        return Result.ok(result);
    }

    @DeleteMapping("/tokens/{id}")
    public Result<Void> revokeToken(@PathVariable String id) {
        checkSuperAdmin();
        tokenService.revoke(id);
        record("REVOKE", "撤销 MCP Token：" + id);
        return Result.ok();
    }

    private void checkSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40101, "无操作权限");
        }
    }

    private void record(String action, String detail) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setId(UUID.randomUUID().toString());
            opLog.setTeamId(null);
            opLog.setUserId(SecurityContextUtil.getCurrentUserId());
            opLog.setUsername(SecurityContextUtil.getCurrentUsername());
            opLog.setAction(action);
            opLog.setTargetType("MCP_SETTINGS");
            opLog.setTargetId("mcp");
            opLog.setTargetName("MCP Server");
            opLog.setDetail(detail);
            opLog.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            logService.logOperation(opLog);
        } catch (Exception ignored) {
            // 审计失败不阻断操作
        }
    }
}
