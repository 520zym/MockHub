package com.mockhub.log.service;

import com.mockhub.common.model.PageResult;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.model.RequestLog;

/**
 * 日志服务接口
 * <p>
 * 提供操作日志和请求日志的写入与查询能力。
 */
public interface LogService {

    /**
     * 异步写入请求日志（Mock 分发后调用，不阻塞响应）
     *
     * @param log 请求日志对象，所有字段由调用方填充
     */
    void asyncLogRequest(RequestLog log);

    /**
     * 同步写入操作日志
     *
     * @param log 操作日志对象，所有字段由调用方填充
     */
    void logOperation(OperationLog log);

    /**
     * 分页查询操作日志
     *
     * @param teamId 团队 ID
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<OperationLog> getOperationLogs(String teamId, int page, int size);

    /**
     * 分页查询请求日志
     *
     * @param teamId 团队 ID
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<RequestLog> getRequestLogs(String teamId, int page, int size);
}
