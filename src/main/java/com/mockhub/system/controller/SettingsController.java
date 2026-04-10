package com.mockhub.system.controller;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.Result;
import com.mockhub.common.config.LogRetainProperties;
import com.mockhub.common.config.MockCorsProperties;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.system.model.dto.SettingsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
     * 校验当前用户是否为超级管理员
     */
    private void checkSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdmin()) {
            throw new BizException(40101, "无操作权限");
        }
    }
}
