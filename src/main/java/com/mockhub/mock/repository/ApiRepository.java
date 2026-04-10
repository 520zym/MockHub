package com.mockhub.mock.repository;

import com.mockhub.mock.model.entity.ApiDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 接口定义数据访问层
 * <p>
 * 使用 JdbcTemplate 操作 api_definition 表。
 * 支持分页、多条件筛选、排序查询。
 */
@Repository
public class ApiRepository {

    private static final Logger log = LoggerFactory.getLogger(ApiRepository.class);

    private final JdbcTemplate jdbcTemplate;

    /** api_definition 表行映射器 */
    private static final RowMapper<ApiDefinition> ROW_MAPPER = new RowMapper<ApiDefinition>() {
        @Override
        public ApiDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiDefinition api = new ApiDefinition();
            api.setId(rs.getString("id"));
            api.setTeamId(rs.getString("team_id"));
            api.setGroupId(rs.getString("group_id"));
            api.setType(rs.getString("type"));
            api.setName(rs.getString("name"));
            api.setMethod(rs.getString("method"));
            api.setPath(rs.getString("path"));
            api.setResponseCode(rs.getInt("response_code"));
            api.setContentType(rs.getString("content_type"));
            api.setResponseBody(rs.getString("response_body"));
            api.setDelayMs(rs.getInt("delay_ms"));
            api.setEnabled(rs.getInt("enabled") == 1);
            api.setGlobalHeaderOverrides(rs.getString("global_header_overrides"));
            api.setSoapConfig(rs.getString("soap_config"));
            api.setScenarios(rs.getString("scenarios"));
            api.setCreatedBy(rs.getString("created_by"));
            api.setCreatedAt(rs.getString("created_at"));
            api.setUpdatedAt(rs.getString("updated_at"));
            api.setUpdatedBy(rs.getString("updated_by"));
            return api;
        }
    };

    /**
     * 列表查询专用的行映射器（不读取 response_body，避免大字段影响列表性能）
     */
    private static final RowMapper<ApiDefinition> LIST_ROW_MAPPER = new RowMapper<ApiDefinition>() {
        @Override
        public ApiDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiDefinition api = new ApiDefinition();
            api.setId(rs.getString("id"));
            api.setTeamId(rs.getString("team_id"));
            api.setGroupId(rs.getString("group_id"));
            api.setType(rs.getString("type"));
            api.setName(rs.getString("name"));
            api.setMethod(rs.getString("method"));
            api.setPath(rs.getString("path"));
            api.setResponseCode(rs.getInt("response_code"));
            api.setContentType(rs.getString("content_type"));
            api.setDelayMs(rs.getInt("delay_ms"));
            api.setEnabled(rs.getInt("enabled") == 1);
            api.setGlobalHeaderOverrides(rs.getString("global_header_overrides"));
            api.setSoapConfig(rs.getString("soap_config"));
            api.setCreatedBy(rs.getString("created_by"));
            api.setCreatedAt(rs.getString("created_at"));
            api.setUpdatedAt(rs.getString("updated_at"));
            api.setUpdatedBy(rs.getString("updated_by"));
            return api;
        }
    };

    public ApiRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据 ID 查询接口定义（含 responseBody）
     *
     * @param id 接口 ID
     * @return 接口定义，未找到返回 null
     */
    public ApiDefinition findById(String id) {
        List<ApiDefinition> list = jdbcTemplate.query(
                "SELECT * FROM api_definition WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询团队内指定方法且已启用的接口（Mock 分发用）
     * <p>
     * 返回全部字段（含 responseBody），因为分发时需要响应体。
     *
     * @param teamId  团队 ID
     * @param method  HTTP 方法
     * @param enabled 是否启用
     * @return 匹配的接口列表
     */
    public List<ApiDefinition> findByTeamIdAndMethodAndEnabled(String teamId, String method, boolean enabled) {
        return jdbcTemplate.query(
                "SELECT * FROM api_definition WHERE team_id = ? AND method = ? AND enabled = ?",
                ROW_MAPPER, teamId, method, enabled ? 1 : 0);
    }

    /**
     * 查询团队内指定路径和方法的接口（唯一性检查用）
     *
     * @param teamId 团队 ID
     * @param path   请求路径
     * @param method HTTP 方法
     * @return 匹配的接口列表（正常情况最多一条）
     */
    public List<ApiDefinition> findByTeamIdAndPathAndMethod(String teamId, String path, String method) {
        return jdbcTemplate.query(
                "SELECT * FROM api_definition WHERE team_id = ? AND path = ? AND method = ?",
                ROW_MAPPER, teamId, path, method);
    }

    /**
     * 分页查询接口列表（支持多条件筛选）
     * <p>
     * 不返回 response_body 字段，避免大字段影响列表查询性能。
     *
     * @param teamIds  可访问的团队 ID 列表
     * @param teamId   按团队筛选（可为 null）
     * @param groupId  按分组筛选（可为 null）
     * @param method   按方法筛选（可为 null）
     * @param enabled  按启用状态筛选（可为 null）
     * @param keyword  按名称或路径模糊搜索（可为 null）
     * @param tagId    按标签筛选（可为 null）
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 接口列表（不含 responseBody）
     */
    public List<ApiDefinition> findAll(List<String> teamIds, String teamId, String groupId,
                                       String method, Boolean enabled, String keyword,
                                       String tagId, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.team_id, a.group_id, a.type, a.name, a.method, a.path, ");
        sql.append("a.response_code, a.content_type, a.delay_ms, a.enabled, ");
        sql.append("a.global_header_overrides, a.soap_config, ");
        sql.append("a.created_by, a.created_at, a.updated_at, a.updated_by ");
        sql.append("FROM api_definition a ");

        // 如果按标签筛选，需要 JOIN api_tag 表
        if (tagId != null && !tagId.isEmpty()) {
            sql.append("INNER JOIN api_tag at ON a.id = at.api_id AND at.tag_id = ? ");
        }

        sql.append("WHERE 1=1 ");

        List<Object> params = new ArrayList<Object>();
        if (tagId != null && !tagId.isEmpty()) {
            params.add(tagId);
        }

        // 团队范围限制
        if (teamId != null && !teamId.isEmpty()) {
            sql.append("AND a.team_id = ? ");
            params.add(teamId);
        } else if (teamIds != null && !teamIds.isEmpty()) {
            sql.append("AND a.team_id IN (");
            for (int i = 0; i < teamIds.size(); i++) {
                sql.append(i > 0 ? ",?" : "?");
                params.add(teamIds.get(i));
            }
            sql.append(") ");
        }

        if (groupId != null && !groupId.isEmpty()) {
            sql.append("AND a.group_id = ? ");
            params.add(groupId);
        }

        if (method != null && !method.isEmpty()) {
            sql.append("AND a.method = ? ");
            params.add(method);
        }

        if (enabled != null) {
            sql.append("AND a.enabled = ? ");
            params.add(enabled ? 1 : 0);
        }

        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (a.name LIKE ? OR a.path LIKE ?) ");
            String likePattern = "%" + keyword + "%";
            params.add(likePattern);
            params.add(likePattern);
        }

        sql.append("ORDER BY a.updated_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), LIST_ROW_MAPPER, params.toArray());
    }

    /**
     * 统计接口总数（支持多条件筛选，与 findAll 条件对应）
     */
    public long count(List<String> teamIds, String teamId, String groupId,
                      String method, Boolean enabled, String keyword, String tagId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM api_definition a ");

        if (tagId != null && !tagId.isEmpty()) {
            sql.append("INNER JOIN api_tag at ON a.id = at.api_id AND at.tag_id = ? ");
        }

        sql.append("WHERE 1=1 ");

        List<Object> params = new ArrayList<Object>();
        if (tagId != null && !tagId.isEmpty()) {
            params.add(tagId);
        }

        if (teamId != null && !teamId.isEmpty()) {
            sql.append("AND a.team_id = ? ");
            params.add(teamId);
        } else if (teamIds != null && !teamIds.isEmpty()) {
            sql.append("AND a.team_id IN (");
            for (int i = 0; i < teamIds.size(); i++) {
                sql.append(i > 0 ? ",?" : "?");
                params.add(teamIds.get(i));
            }
            sql.append(") ");
        }

        if (groupId != null && !groupId.isEmpty()) {
            sql.append("AND a.group_id = ? ");
            params.add(groupId);
        }

        if (method != null && !method.isEmpty()) {
            sql.append("AND a.method = ? ");
            params.add(method);
        }

        if (enabled != null) {
            sql.append("AND a.enabled = ? ");
            params.add(enabled ? 1 : 0);
        }

        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (a.name LIKE ? OR a.path LIKE ?) ");
            String likePattern = "%" + keyword + "%";
            params.add(likePattern);
            params.add(likePattern);
        }

        Long result = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return result != null ? result : 0L;
    }

    /**
     * 插入接口定义
     */
    public void insert(ApiDefinition api) {
        jdbcTemplate.update(
                "INSERT INTO api_definition (id, team_id, group_id, type, name, method, path, " +
                        "response_code, content_type, response_body, delay_ms, enabled, " +
                        "global_header_overrides, soap_config, scenarios, created_by, created_at, updated_at, updated_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                api.getId(), api.getTeamId(), api.getGroupId(), api.getType(), api.getName(),
                api.getMethod(), api.getPath(), api.getResponseCode(), api.getContentType(),
                api.getResponseBody(), api.getDelayMs(), api.isEnabled() ? 1 : 0,
                api.getGlobalHeaderOverrides(), api.getSoapConfig(), api.getScenarios(),
                api.getCreatedBy(), api.getCreatedAt(), api.getUpdatedAt(), api.getUpdatedBy());
    }

    /**
     * 更新接口定义
     */
    public void update(ApiDefinition api) {
        jdbcTemplate.update(
                "UPDATE api_definition SET team_id = ?, group_id = ?, type = ?, name = ?, method = ?, path = ?, " +
                        "response_code = ?, content_type = ?, response_body = ?, delay_ms = ?, enabled = ?, " +
                        "global_header_overrides = ?, soap_config = ?, scenarios = ?, " +
                        "updated_at = ?, updated_by = ? WHERE id = ?",
                api.getTeamId(), api.getGroupId(), api.getType(), api.getName(),
                api.getMethod(), api.getPath(), api.getResponseCode(), api.getContentType(),
                api.getResponseBody(), api.getDelayMs(), api.isEnabled() ? 1 : 0,
                api.getGlobalHeaderOverrides(), api.getSoapConfig(), api.getScenarios(),
                api.getUpdatedAt(), api.getUpdatedBy(), api.getId());
    }

    /**
     * 删除接口定义
     *
     * @param id 接口 ID
     * @return 受影响行数
     */
    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM api_definition WHERE id = ?", id);
    }

    /**
     * 统计团队下的接口数量
     */
    public long countByTeamId(String teamId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE team_id = ?", Long.class, teamId);
        return count != null ? count : 0L;
    }

    /**
     * 统计分组下的接口数量
     */
    public long countByGroupId(String groupId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE group_id = ?", Long.class, groupId);
        return count != null ? count : 0L;
    }

    /**
     * 将指定分组下所有接口的 groupId 置为 null（删除分组时调用）
     *
     * @param groupId 分组 ID
     */
    public void clearGroupId(String groupId) {
        jdbcTemplate.update(
                "UPDATE api_definition SET group_id = NULL WHERE group_id = ?", groupId);
    }

    /**
     * 切换接口启用/禁用状态
     *
     * @param id      接口 ID
     * @param enabled 新的启用状态
     * @param now     更新时间
     */
    public void updateEnabled(String id, boolean enabled, String now) {
        jdbcTemplate.update(
                "UPDATE api_definition SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, now, id);
    }

    /**
     * 查询团队下所有接口（导出用，含 responseBody）
     */
    public List<ApiDefinition> findByTeamId(String teamId) {
        return jdbcTemplate.query(
                "SELECT * FROM api_definition WHERE team_id = ? ORDER BY created_at",
                ROW_MAPPER, teamId);
    }
}
