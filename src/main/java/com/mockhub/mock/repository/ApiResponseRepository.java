package com.mockhub.mock.repository;

import com.mockhub.mock.model.entity.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 接口返回体数据访问层
 * <p>
 * 操作 api_response 表，支持 REST 和 SOAP 的多返回体管理。
 * REST 接口的 soap_operation_name 为 null，SOAP 按 operationName 区分。
 */
@Repository
public class ApiResponseRepository {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseRepository.class);

    private final JdbcTemplate jdbcTemplate;

    /** 完整行映射器（含 response_body） */
    private static final RowMapper<ApiResponse> ROW_MAPPER = new RowMapper<ApiResponse>() {
        @Override
        public ApiResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiResponse resp = new ApiResponse();
            resp.setId(rs.getString("id"));
            resp.setApiId(rs.getString("api_id"));
            resp.setSoapOperationName(rs.getString("soap_operation_name"));
            resp.setName(rs.getString("name"));
            resp.setResponseCode(rs.getInt("response_code"));
            resp.setContentType(rs.getString("content_type"));
            resp.setResponseBody(rs.getString("response_body"));
            resp.setDelayMs(rs.getInt("delay_ms"));
            resp.setActive(rs.getInt("is_active") == 1);
            resp.setSortOrder(rs.getInt("sort_order"));
            resp.setConditions(rs.getString("conditions"));
            resp.setCreatedAt(rs.getString("created_at"));
            resp.setUpdatedAt(rs.getString("updated_at"));
            return resp;
        }
    };

    /** 列表行映射器（不含 response_body，用于统计和摘要查询） */
    private static final RowMapper<ApiResponse> SUMMARY_ROW_MAPPER = new RowMapper<ApiResponse>() {
        @Override
        public ApiResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiResponse resp = new ApiResponse();
            resp.setId(rs.getString("id"));
            resp.setApiId(rs.getString("api_id"));
            resp.setSoapOperationName(rs.getString("soap_operation_name"));
            resp.setName(rs.getString("name"));
            resp.setResponseCode(rs.getInt("response_code"));
            resp.setContentType(rs.getString("content_type"));
            resp.setDelayMs(rs.getInt("delay_ms"));
            resp.setActive(rs.getInt("is_active") == 1);
            resp.setSortOrder(rs.getInt("sort_order"));
            resp.setCreatedAt(rs.getString("created_at"));
            resp.setUpdatedAt(rs.getString("updated_at"));
            return resp;
        }
    };

    public ApiResponseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询接口的所有返回体（含 response_body），按 soap_operation_name 和 sort_order 排序
     *
     * @param apiId 接口 ID
     * @return 返回体列表
     */
    public List<ApiResponse> findByApiId(String apiId) {
        return jdbcTemplate.query(
                "SELECT * FROM api_response WHERE api_id = ? ORDER BY soap_operation_name, sort_order",
                ROW_MAPPER, apiId);
    }

    /**
     * 查询指定 SOAP operation 的所有返回体
     *
     * @param apiId             接口 ID
     * @param soapOperationName SOAP operation 名称
     * @return 返回体列表
     */
    public List<ApiResponse> findByApiIdAndOperation(String apiId, String soapOperationName) {
        return jdbcTemplate.query(
                "SELECT * FROM api_response WHERE api_id = ? AND soap_operation_name = ? ORDER BY sort_order",
                ROW_MAPPER, apiId, soapOperationName);
    }

    /**
     * 查询 REST 接口的活跃返回体（soap_operation_name IS NULL 且 is_active=1）
     * <p>
     * 历史单选语义：同一接口仅一条 active。v1.4.3 起 is_active 语义扩展为"启用"，
     * 多条可同时 active；此方法仍保留 LIMIT 1 供 SOAP 分支和旧代码回退使用。
     * REST 主路径改走 {@link #findEnabledByApiId(String)}。
     *
     * @param apiId 接口 ID
     * @return 活跃返回体，无则返回 null
     */
    public ApiResponse findActiveByApiId(String apiId) {
        List<ApiResponse> list = jdbcTemplate.query(
                "SELECT * FROM api_response WHERE api_id = ? AND soap_operation_name IS NULL AND is_active = 1 LIMIT 1",
                ROW_MAPPER, apiId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询 REST 接口所有启用的返回体，按 sort_order 升序排列。
     * <p>
     * v1.4.3 新增，用于条件响应匹配引擎 {@code ResponseMatcher}。
     * 启用数 == 1 时 matcher 会短路直接返回该条；启用数 >= 2 时按顺序遍历做条件匹配。
     *
     * @param apiId 接口 ID
     * @return 启用返回体列表，按 sort_order 升序
     */
    public List<ApiResponse> findEnabledByApiId(String apiId) {
        return jdbcTemplate.query(
                "SELECT * FROM api_response WHERE api_id = ? AND soap_operation_name IS NULL AND is_active = 1 ORDER BY sort_order ASC",
                ROW_MAPPER, apiId);
    }

    /**
     * 查询指定 SOAP operation 的活跃返回体
     *
     * @param apiId             接口 ID
     * @param soapOperationName SOAP operation 名称
     * @return 活跃返回体，无则返回 null
     */
    public ApiResponse findActiveByApiIdAndOperation(String apiId, String soapOperationName) {
        List<ApiResponse> list = jdbcTemplate.query(
                "SELECT * FROM api_response WHERE api_id = ? AND soap_operation_name = ? AND is_active = 1 LIMIT 1",
                ROW_MAPPER, apiId, soapOperationName);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询接口的返回体摘要信息（不含 response_body，用于列表展示）
     *
     * @param apiId 接口 ID
     * @return 返回体摘要列表
     */
    public List<ApiResponse> findSummaryByApiId(String apiId) {
        return jdbcTemplate.query(
                "SELECT id, api_id, soap_operation_name, name, response_code, content_type, delay_ms, " +
                "is_active, sort_order, created_at, updated_at FROM api_response " +
                "WHERE api_id = ? ORDER BY soap_operation_name, sort_order",
                SUMMARY_ROW_MAPPER, apiId);
    }

    /**
     * 统计接口的返回体数量
     *
     * @param apiId 接口 ID
     * @return 返回体数量
     */
    public int countByApiId(String apiId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_response WHERE api_id = ?", Integer.class, apiId);
        return count != null ? count : 0;
    }

    /**
     * 插入返回体
     */
    public void insert(ApiResponse resp) {
        jdbcTemplate.update(
                "INSERT INTO api_response (id, api_id, soap_operation_name, name, response_code, " +
                "content_type, response_body, delay_ms, is_active, sort_order, conditions, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                resp.getId(), resp.getApiId(), resp.getSoapOperationName(), resp.getName(),
                resp.getResponseCode(), resp.getContentType(), resp.getResponseBody(),
                resp.getDelayMs(), resp.isActive() ? 1 : 0, resp.getSortOrder(),
                resp.getConditions(), resp.getCreatedAt(), resp.getUpdatedAt());
    }

    /**
     * 删除接口的所有返回体
     *
     * @param apiId 接口 ID
     */
    public void deleteByApiId(String apiId) {
        int deleted = jdbcTemplate.update("DELETE FROM api_response WHERE api_id = ?", apiId);
        if (deleted > 0) {
            log.debug("删除接口 {} 的 {} 个返回体", apiId, deleted);
        }
    }

    /**
     * 批量删除多个接口的返回体（批量删除接口时调用）
     *
     * @param apiIds 接口 ID 列表
     * @return 受影响行数
     */
    public int batchDeleteByApiIds(List<String> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM api_response WHERE api_id IN (");
        for (int i = 0; i < apiIds.size(); i++) {
            sql.append(i > 0 ? ",?" : "?");
        }
        sql.append(")");
        int deleted = jdbcTemplate.update(sql.toString(), apiIds.toArray());
        if (deleted > 0) {
            log.debug("批量删除 {} 个接口的返回体，共 {} 条", apiIds.size(), deleted);
        }
        return deleted;
    }

    /**
     * 替换接口的所有返回体（先删后批量插入，用于保存时整体替换）
     *
     * @param apiId     接口 ID
     * @param responses 新的返回体列表
     */
    public void replaceAll(String apiId, List<ApiResponse> responses) {
        deleteByApiId(apiId);
        for (ApiResponse resp : responses) {
            insert(resp);
        }
        log.debug("替换接口 {} 的返回体，共 {} 个", apiId, responses.size());
    }
}
