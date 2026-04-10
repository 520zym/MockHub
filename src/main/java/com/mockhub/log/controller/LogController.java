package com.mockhub.log.controller;

import com.mockhub.common.model.PageResult;
import com.mockhub.common.model.Result;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.model.RequestLog;
import com.mockhub.log.service.LogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志查询控制器
 * <p>
 * 提供操作日志和请求日志的分页查询接口。
 * 所有查询都需要 JWT 认证，权限校验由 Service 层处理。
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    /**
     * 分页查询操作日志
     *
     * @param teamId 团队 ID（必填）
     * @param page   页码，默认 1
     * @param size   每页条数，默认 50
     * @return 分页操作日志
     */
    @GetMapping("/operation")
    public Result<PageResult<OperationLog>> getOperationLogs(
            @RequestParam String teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.ok(logService.getOperationLogs(teamId, page, size));
    }

    /**
     * 分页查询请求日志
     *
     * @param teamId 团队 ID（必填）
     * @param page   页码，默认 1
     * @param size   每页条数，默认 50
     * @return 分页请求日志
     */
    @GetMapping("/request")
    public Result<PageResult<RequestLog>> getRequestLogs(
            @RequestParam String teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.ok(logService.getRequestLogs(teamId, page, size));
    }
}
