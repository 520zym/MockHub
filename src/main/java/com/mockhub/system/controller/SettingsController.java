package com.mockhub.system.controller;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.Result;
import com.mockhub.common.config.LogRetainProperties;
import com.mockhub.common.config.MockCorsProperties;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.service.MockFileMaintenanceService;
import com.mockhub.mock.service.MockFileStorageService;
import com.mockhub.system.model.dto.SettingsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局设置控制器
 * <p>
 * 提供系统级配置的读取和修改功能，仅超级管理员可访问。
 * 当前实现将配置存储在内存中（通过 Spring 配置属性对象），
 * 修改后立即生效但不持久化到文件（重启后恢复默认值或启动参数值）。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    @Autowired
    private LogRetainProperties logRetainProperties;

    @Autowired
    private MockCorsProperties mockCorsProperties;

    @Autowired
    private HealthController healthController;

    @Autowired
    private MockFileMaintenanceService mockFileMaintenanceService;

    @Autowired
    private LogService logService;

    /**
     * GET /api/settings — 获取全局配置
     *
     * @return 当前配置
     */
    @GetMapping
    public Result<SettingsDTO> get() {
        checkSuperAdmin();

        SettingsDTO dto = new SettingsDTO();
        dto.setLogRetainMode(logRetainProperties.getMode());
        dto.setLogRetainCount(logRetainProperties.getCount());
        dto.setLogRetainDays(logRetainProperties.getDays());
        dto.setMockCorsEnabled(mockCorsProperties.isEnabled());
        dto.setServerAddress(healthController.getCustomServerAddress());

        return Result.ok(dto);
    }

    /**
     * PUT /api/settings — 保存全局配置
     * <p>
     * 修改后立即生效（内存中更新），不持久化到配置文件。
     *
     * @param dto 新的配置值
     * @return 更新后的配置
     */
    @PutMapping
    public Result<SettingsDTO> save(@RequestBody SettingsDTO dto) {
        checkSuperAdmin();

        log.info("更新全局设置：logRetainMode={}, logRetainCount={}, logRetainDays={}, mockCorsEnabled={}",
                dto.getLogRetainMode(), dto.getLogRetainCount(), dto.getLogRetainDays(), dto.isMockCorsEnabled());

        logRetainProperties.setMode(dto.getLogRetainMode());
        logRetainProperties.setCount(dto.getLogRetainCount());
        logRetainProperties.setDays(dto.getLogRetainDays());
        mockCorsProperties.setEnabled(dto.isMockCorsEnabled());
        healthController.setCustomServerAddress(dto.getServerAddress());

        return Result.ok(dto);
    }

    /**
     * GET /api/settings/file-storage/orphans — 扫描文件响应存储中的孤儿文件
     */
    @GetMapping("/file-storage/orphans")
    public Result<MockFileMaintenanceService.OrphanScanResult> scanOrphanFiles() {
        checkSuperAdmin();
        MockFileMaintenanceService.OrphanScanResult result = mockFileMaintenanceService.scanOrphanFiles();
        recordSystemOperation("SCAN", "扫描文件存储孤儿文件：发现 " + result.getOrphanCount()
                + " 个，合计 " + result.getOrphanSize() + " 字节");
        return Result.ok(result);
    }

    /**
     * DELETE /api/settings/file-storage/orphans — 清理未被任何返回体引用的文件
     */
    @DeleteMapping("/file-storage/orphans")
    public Result<MockFileStorageService.OrphanCleanupResult> cleanOrphanFiles() {
        checkSuperAdmin();
        MockFileStorageService.OrphanCleanupResult result = mockFileMaintenanceService.cleanOrphanFiles();
        recordSystemOperation("CLEAN", "清理文件存储孤儿文件：删除 " + result.getDeletedCount()
                + " 个，合计 " + result.getDeletedSize() + " 字节");
        return Result.ok(result);
    }

    /**
     * GET /api/settings/file-storage/stats — 获取文件存储诊断信息
     */
    @GetMapping("/file-storage/stats")
    public Result<MockFileStorageService.StorageStats> getFileStorageStats() {
        checkSuperAdmin();
        return Result.ok(mockFileMaintenanceService.getStorageStats());
    }

    /**
     * 校验当前用户是否为超级管理员
     */
    private void checkSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40101, "无操作权限");
        }
    }

    private void recordSystemOperation(String action, String detail) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setId(java.util.UUID.randomUUID().toString());
            opLog.setTeamId(null);
            opLog.setUserId(SecurityContextUtil.getCurrentUserId());
            opLog.setUsername(SecurityContextUtil.getCurrentUsername());
            opLog.setAction(action);
            opLog.setTargetType("FILE_STORAGE");
            opLog.setTargetId("mock-files");
            opLog.setTargetName("Mock 文件存储");
            opLog.setDetail(detail);
            opLog.setCreatedAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                    .format(new java.util.Date()));
            logService.logOperation(opLog);
        } catch (Exception e) {
            log.warn("记录文件存储维护操作日志失败: {}", e.getMessage());
        }
    }
}
