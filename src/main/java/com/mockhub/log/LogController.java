package com.mockhub.log;

import com.mockhub.common.model.PageResult;
import com.mockhub.common.model.Result;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.model.RequestLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日志查询控制器
 * <p>
 * 提供操作日志和请求日志的分页查询接口。
 * 所有查询都需要 JWT 认证，并校验当前用户是否有权访问目标团队。
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogRepository logRepository;
    private final PermissionChecker permissionChecker;

    public LogController(LogRepository logRepository, PermissionChecker permissionChecker) {
        this.logRepository = logRepository;
        this.permissionChecker = permissionChecker;
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

        permissionChecker.checkTeamAccess(teamId);

        long total = logRepository.countOperationLogs(teamId);
        List<OperationLog> items = logRepository.findOperationLogs(teamId, page, size);

        return Result.ok(PageResult.of(items, total, page, size));
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

        permissionChecker.checkTeamAccess(teamId);

        long total = logRepository.countRequestLogs(teamId);
        List<RequestLog> items = logRepository.findRequestLogs(teamId, page, size);

        return Result.ok(PageResult.of(items, total, page, size));
    }
}
