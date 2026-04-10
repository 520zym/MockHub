package com.mockhub.log;

import com.mockhub.log.model.OperationLog;
import com.mockhub.log.model.RequestLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 日志数据访问层
 * <p>
 * 通过 JdbcTemplate 操作 operation_log 和 request_log 表。
 * 提供写入、分页查询和保留策略清理方法。
 */
@Repository
public class LogRepository {

    private static final Logger logger = LoggerFactory.getLogger(LogRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public LogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== OperationLog =====

    /**
     * 插入一条操作日志
     *
     * @param log 操作日志对象，所有字段由调用方填充
     */
    public void insertOperationLog(OperationLog log) {
        String sql = "INSERT INTO operation_log (id, team_id, user_id, username, action, target_type, target_id, target_name, detail, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                log.getId(),
                log.getTeamId(),
                log.getUserId(),
                log.getUsername(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTargetName(),
                log.getDetail(),
                log.getCreatedAt());
    }

    /**
     * 分页查询操作日志
     *
     * @param teamId 团队 ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 操作日志列表
     */
    public List<OperationLog> findOperationLogs(String teamId, int page, int size) {
        String sql = "SELECT * FROM operation_log WHERE team_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        int offset = (page - 1) * size;
        return jdbcTemplate.query(sql, OPERATION_LOG_ROW_MAPPER, teamId, size, offset);
    }

    /**
     * 查询操作日志总数
     *
     * @param teamId 团队 ID
     * @return 总条数
     */
    public long countOperationLogs(String teamId) {
        String sql = "SELECT COUNT(*) FROM operation_log WHERE team_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, teamId);
        return count != null ? count : 0L;
    }

    /**
     * 按条数清理操作日志：保留最新 maxCount 条，删除更早的记录
     *
     * @param maxCount 最大保留条数
     * @return 删除的记录数
     */
    public int cleanOperationLogsByCount(int maxCount) {
        String sql = "DELETE FROM operation_log WHERE id NOT IN "
                + "(SELECT id FROM operation_log ORDER BY created_at DESC LIMIT ?)";
        return jdbcTemplate.update(sql, maxCount);
    }

    /**
     * 按天数清理操作日志：删除超过 N 天的记录
     *
     * @param days 保留天数
     * @return 删除的记录数
     */
    public int cleanOperationLogsByDays(int days) {
        String sql = "DELETE FROM operation_log WHERE created_at < datetime('now', '-' || ? || ' days')";
        return jdbcTemplate.update(sql, days);
    }

    // ===== RequestLog =====

    /**
     * 插入一条请求日志
     *
     * @param log 请求日志对象，所有字段由调用方填充
     */
    public void insertRequestLog(RequestLog log) {
        String sql = "INSERT INTO request_log (id, team_id, api_id, api_path, method, request_headers, request_body, request_params, response_code, duration_ms, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                log.getId(),
                log.getTeamId(),
                log.getApiId(),
                log.getApiPath(),
                log.getMethod(),
                log.getRequestHeaders(),
                log.getRequestBody(),
                log.getRequestParams(),
                log.getResponseCode(),
                log.getDurationMs(),
                log.getCreatedAt());
    }

    /**
     * 分页查询请求日志
     *
     * @param teamId 团队 ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 请求日志列表
     */
    public List<RequestLog> findRequestLogs(String teamId, int page, int size) {
        String sql = "SELECT * FROM request_log WHERE team_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        int offset = (page - 1) * size;
        return jdbcTemplate.query(sql, REQUEST_LOG_ROW_MAPPER, teamId, size, offset);
    }

    /**
     * 查询请求日志总数
     *
     * @param teamId 团队 ID
     * @return 总条数
     */
    public long countRequestLogs(String teamId) {
        String sql = "SELECT COUNT(*) FROM request_log WHERE team_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, teamId);
        return count != null ? count : 0L;
    }

    /**
     * 按条数清理请求日志：保留最新 maxCount 条，删除更早的记录
     *
     * @param maxCount 最大保留条数
     * @return 删除的记录数
     */
    public int cleanRequestLogsByCount(int maxCount) {
        String sql = "DELETE FROM request_log WHERE id NOT IN "
                + "(SELECT id FROM request_log ORDER BY created_at DESC LIMIT ?)";
        return jdbcTemplate.update(sql, maxCount);
    }

    /**
     * 按天数清理请求日志：删除超过 N 天的记录
     *
     * @param days 保留天数
     * @return 删除的记录数
     */
    public int cleanRequestLogsByDays(int days) {
        String sql = "DELETE FROM request_log WHERE created_at < datetime('now', '-' || ? || ' days')";
        return jdbcTemplate.update(sql, days);
    }

    // ===== RowMapper =====

    private static final RowMapper<OperationLog> OPERATION_LOG_ROW_MAPPER = new RowMapper<OperationLog>() {
        @Override
        public OperationLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            OperationLog log = new OperationLog();
            log.setId(rs.getString("id"));
            log.setTeamId(rs.getString("team_id"));
            log.setUserId(rs.getString("user_id"));
            log.setUsername(rs.getString("username"));
            log.setAction(rs.getString("action"));
            log.setTargetType(rs.getString("target_type"));
            log.setTargetId(rs.getString("target_id"));
            log.setTargetName(rs.getString("target_name"));
            log.setDetail(rs.getString("detail"));
            log.setCreatedAt(rs.getString("created_at"));
            return log;
        }
    };

    private static final RowMapper<RequestLog> REQUEST_LOG_ROW_MAPPER = new RowMapper<RequestLog>() {
        @Override
        public RequestLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            RequestLog log = new RequestLog();
            log.setId(rs.getString("id"));
            log.setTeamId(rs.getString("team_id"));
            log.setApiId(rs.getString("api_id"));
            log.setApiPath(rs.getString("api_path"));
            log.setMethod(rs.getString("method"));
            log.setRequestHeaders(rs.getString("request_headers"));
            log.setRequestBody(rs.getString("request_body"));
            log.setRequestParams(rs.getString("request_params"));
            log.setResponseCode(rs.getInt("response_code"));
            log.setDurationMs(rs.getLong("duration_ms"));
            log.setCreatedAt(rs.getString("created_at"));
            return log;
        }
    };
}
