package com.mockhub.log;

import com.mockhub.common.config.LogRetainProperties;
import com.mockhub.log.model.OperationLog;
import com.mockhub.log.model.RequestLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * 日志服务实现
 * <p>
 * 负责操作日志和请求日志的写入，以及基于保留策略的自动清理。
 * <ul>
 *   <li>请求日志通过 {@code @Async} 异步写入，不阻塞 Mock 响应</li>
 *   <li>每次写入后自动触发清理，根据 {@link LogRetainProperties} 的 mode 选择 count 或 days 模式</li>
 *   <li>应用启动时（{@code @PostConstruct}）执行一次清理，清除历史积压的过期日志</li>
 * </ul>
 */
@Service
public class LogServiceImpl implements LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    private final LogRepository logRepository;
    private final LogRetainProperties logRetainProperties;

    public LogServiceImpl(LogRepository logRepository, LogRetainProperties logRetainProperties) {
        this.logRepository = logRepository;
        this.logRetainProperties = logRetainProperties;
    }

    /**
     * 应用启动时执行一次日志清理，清除可能的历史积压
     */
    @PostConstruct
    public void initClean() {
        logger.info("应用启动，执行日志清理（mode={}，count={}，days={}）",
                logRetainProperties.getMode(), logRetainProperties.getCount(), logRetainProperties.getDays());
        cleanLogs();
    }

    /**
     * 异步写入请求日志
     * <p>
     * 使用 Spring @Async 在独立线程池中执行，不阻塞 Mock 请求的响应返回。
     * 写入完成后自动触发日志清理策略。
     *
     * @param log 请求日志对象
     */
    @Async
    @Override
    public void asyncLogRequest(RequestLog log) {
        try {
            logRepository.insertRequestLog(log);
            logger.debug("写入请求日志：apiPath={}, method={}", log.getApiPath(), log.getMethod());
            cleanRequestLogs();
        } catch (Exception e) {
            logger.error("写入请求日志失败：apiPath={}, method={}", log.getApiPath(), log.getMethod(), e);
        }
    }

    /**
     * 同步写入操作日志
     * <p>
     * 写入完成后自动触发日志清理策略。
     *
     * @param log 操作日志对象
     */
    @Override
    public void logOperation(OperationLog log) {
        try {
            logRepository.insertOperationLog(log);
            logger.debug("写入操作日志：action={}, targetType={}, targetName={}",
                    log.getAction(), log.getTargetType(), log.getTargetName());
            cleanOperationLogs();
        } catch (Exception e) {
            logger.error("写入操作日志失败：action={}, targetType={}, targetName={}",
                    log.getAction(), log.getTargetType(), log.getTargetName(), e);
        }
    }

    /**
     * 执行所有类型日志的清理
     */
    private void cleanLogs() {
        cleanOperationLogs();
        cleanRequestLogs();
    }

    /**
     * 根据保留策略清理操作日志
     * <p>
     * 清理策略由 {@link LogRetainProperties} 决定：
     * <ul>
     *   <li>count 模式：保留最新 N 条，删除更早的记录</li>
     *   <li>days 模式：保留最近 N 天的记录，删除更早的记录</li>
     * </ul>
     */
    private void cleanOperationLogs() {
        try {
            int deleted;
            if ("days".equalsIgnoreCase(logRetainProperties.getMode())) {
                deleted = logRepository.cleanOperationLogsByDays(logRetainProperties.getDays());
            } else {
                deleted = logRepository.cleanOperationLogsByCount(logRetainProperties.getCount());
            }
            if (deleted > 0) {
                logger.info("清理操作日志 {} 条（mode={}）", deleted, logRetainProperties.getMode());
            }
        } catch (Exception e) {
            logger.error("清理操作日志失败", e);
        }
    }

    /**
     * 根据保留策略清理请求日志
     * <p>
     * 清理策略同操作日志，详见 {@link #cleanOperationLogs()}。
     */
    private void cleanRequestLogs() {
        try {
            int deleted;
            if ("days".equalsIgnoreCase(logRetainProperties.getMode())) {
                deleted = logRepository.cleanRequestLogsByDays(logRetainProperties.getDays());
            } else {
                deleted = logRepository.cleanRequestLogsByCount(logRetainProperties.getCount());
            }
            if (deleted > 0) {
                logger.info("清理请求日志 {} 条（mode={}）", deleted, logRetainProperties.getMode());
            }
        } catch (Exception e) {
            logger.error("清理请求日志失败", e);
        }
    }
}
