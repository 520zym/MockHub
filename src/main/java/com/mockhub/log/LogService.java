package com.mockhub.log;

import com.mockhub.log.model.OperationLog;
import com.mockhub.log.model.RequestLog;

/**
 * 日志服务接口
 * <p>
 * 供 mock 模块引用，提供操作日志和请求日志的写入能力。
 * 放在 com.mockhub.log 包下（而非 service 子包），方便其他模块直接 import。
 */
public interface LogService {

    /**
     * 异步写入请求日志（Mock 分发后调用，不阻塞响应）
     * <p>
     * 内部使用 @Async 实现异步，写入后自动触发日志清理策略。
     *
     * @param log 请求日志对象，所有字段由调用方填充
     */
    void asyncLogRequest(RequestLog log);

    /**
     * 同步写入操作日志
     * <p>
     * 写入后自动触发日志清理策略。
     *
     * @param log 操作日志对象，所有字段由调用方填充
     */
    void logOperation(OperationLog log);
}
