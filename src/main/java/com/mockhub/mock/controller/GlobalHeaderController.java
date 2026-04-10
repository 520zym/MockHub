package com.mockhub.mock.controller;

import com.mockhub.common.model.Result;
import com.mockhub.mock.model.entity.GlobalHeader;
import com.mockhub.mock.service.GlobalHeaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 全局响应头管理 Controller
 * <p>
 * 提供团队级别全局响应头的查询和整体替换端点。
 */
@RestController
@RequestMapping("/api/global-headers")
public class GlobalHeaderController {

    private static final Logger log = LoggerFactory.getLogger(GlobalHeaderController.class);

    private final GlobalHeaderService globalHeaderService;

    public GlobalHeaderController(GlobalHeaderService globalHeaderService) {
        this.globalHeaderService = globalHeaderService;
    }

    /**
     * 查询团队的全局响应头列表
     *
     * @param teamId 团队 ID
     * @return 全局响应头列表（含启用和禁用，按 sortOrder 排序）
     */
    @GetMapping
    public Result<List<GlobalHeader>> list(@RequestParam String teamId) {
        List<GlobalHeader> headers = globalHeaderService.findByTeamId(teamId);
        return Result.ok(headers);
    }

    /**
     * 整体替换团队的全局响应头
     * <p>
     * 前端传入完整的响应头列表，后端先删后插。
     *
     * @param teamId  团队 ID
     * @param headers 新的全局响应头列表
     * @return 空响应
     */
    @PutMapping
    public Result<Void> replaceAll(@RequestParam String teamId,
                                   @RequestBody List<GlobalHeader> headers) {
        globalHeaderService.replaceAll(teamId, headers);
        return Result.ok();
    }
}
