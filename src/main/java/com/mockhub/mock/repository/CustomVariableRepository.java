package com.mockhub.mock.repository;

import com.mockhub.mock.model.entity.CustomVariable;
import com.mockhub.mock.model.entity.CustomVariableGroup;
import com.mockhub.mock.model.entity.CustomVariableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义动态变量数据访问层
 * <p>
 * 使用 JdbcTemplate 操作 custom_variable / custom_variable_value /
 * custom_variable_group / custom_variable_group_value 四张表。
 * 因为 SQLite 默认未启用 PRAGMA foreign_keys，所有删除操作在 Service 层或本类内
 * 手动按正确顺序级联清理（先删关联 → 分组 → 值 → 变量主表）。
 */
@Repository
public class CustomVariableRepository {

    private static final Logger log = LoggerFactory.getLogger(CustomVariableRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public CustomVariableRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** custom_variable 行映射器 */
    private static final RowMapper<CustomVariable> VARIABLE_MAPPER = new RowMapper<CustomVariable>() {
        @Override
        public CustomVariable mapRow(ResultSet rs, int rowNum) throws SQLException {
            CustomVariable v = new CustomVariable();
            v.setId(rs.getString("id"));
            v.setTeamId(rs.getString("team_id"));
            v.setName(rs.getString("name"));
            v.setDescription(rs.getString("description"));
            v.setCreatedAt(rs.getString("created_at"));
            v.setUpdatedAt(rs.getString("updated_at"));
            return v;
        }
    };

    /** custom_variable_value 行映射器 */
    private static final RowMapper<CustomVariableValue> VALUE_MAPPER = new RowMapper<CustomVariableValue>() {
        @Override
        public CustomVariableValue mapRow(ResultSet rs, int rowNum) throws SQLException {
            CustomVariableValue v = new CustomVariableValue();
            v.setId(rs.getString("id"));
            v.setVariableId(rs.getString("variable_id"));
            v.setValue(rs.getString("value"));
            v.setDescription(rs.getString("description"));
            v.setSortOrder(rs.getInt("sort_order"));
            return v;
        }
    };

    /** custom_variable_group 行映射器 */
    private static final RowMapper<CustomVariableGroup> GROUP_MAPPER = new RowMapper<CustomVariableGroup>() {
        @Override
        public CustomVariableGroup mapRow(ResultSet rs, int rowNum) throws SQLException {
            CustomVariableGroup g = new CustomVariableGroup();
            g.setId(rs.getString("id"));
            g.setVariableId(rs.getString("variable_id"));
            g.setName(rs.getString("name"));
            g.setDescription(rs.getString("description"));
            return g;
        }
    };

    // ========== 变量主表 ==========

    /**
     * 查询团队下所有变量
     */
    public List<CustomVariable> findByTeamId(String teamId) {
        return jdbcTemplate.query(
                "SELECT * FROM custom_variable WHERE team_id = ? ORDER BY name",
                VARIABLE_MAPPER, teamId);
    }

    /**
     * 按 ID 查询变量
     */
    public CustomVariable findById(String id) {
        List<CustomVariable> list = jdbcTemplate.query(
                "SELECT * FROM custom_variable WHERE id = ?", VARIABLE_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按团队和变量名查询（用于唯一性校验）
     */
    public CustomVariable findByTeamIdAndName(String teamId, String name) {
        List<CustomVariable> list = jdbcTemplate.query(
                "SELECT * FROM custom_variable WHERE team_id = ? AND name = ?",
                VARIABLE_MAPPER, teamId, name);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 统计团队下的变量数量（硬性护栏用）
     */
    public long countByTeamId(String teamId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_variable WHERE team_id = ?", Long.class, teamId);
        return c != null ? c : 0L;
    }

    /**
     * 插入变量
     */
    public void insertVariable(CustomVariable v) {
        jdbcTemplate.update(
                "INSERT INTO custom_variable (id, team_id, name, description, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                v.getId(), v.getTeamId(), v.getName(), v.getDescription(),
                v.getCreatedAt(), v.getUpdatedAt());
    }

    /**
     * 更新变量（改名、改描述）
     */
    public void updateVariable(CustomVariable v) {
        jdbcTemplate.update(
                "UPDATE custom_variable SET name = ?, description = ?, updated_at = ? WHERE id = ?",
                v.getName(), v.getDescription(), v.getUpdatedAt(), v.getId());
    }

    /**
     * 手动级联删除变量及其所有值、分组、关联
     * <p>
     * 顺序：先清理 group_value 关联 → 分组 → 值 → 变量主表
     * 单事务保证原子性。
     */
    @Transactional
    public void deleteVariableCascade(String variableId) {
        // 1. 删除分组与值的关联（通过变量下的分组 ID 反查）
        jdbcTemplate.update(
                "DELETE FROM custom_variable_group_value WHERE group_id IN " +
                        "(SELECT id FROM custom_variable_group WHERE variable_id = ?)",
                variableId);
        // 2. 删除所有分组
        jdbcTemplate.update(
                "DELETE FROM custom_variable_group WHERE variable_id = ?", variableId);
        // 3. 删除所有值
        jdbcTemplate.update(
                "DELETE FROM custom_variable_value WHERE variable_id = ?", variableId);
        // 4. 删除变量主记录
        jdbcTemplate.update(
                "DELETE FROM custom_variable WHERE id = ?", variableId);
    }

    // ========== 候选值 ==========

    /**
     * 查询变量下所有候选值（按 sort_order 升序，相同则按 value 升序）
     */
    public List<CustomVariableValue> findValuesByVariableId(String variableId) {
        return jdbcTemplate.query(
                "SELECT * FROM custom_variable_value WHERE variable_id = ? ORDER BY sort_order, value",
                VALUE_MAPPER, variableId);
    }

    /**
     * 按 ID 查询值
     */
    public CustomVariableValue findValueById(String id) {
        List<CustomVariableValue> list = jdbcTemplate.query(
                "SELECT * FROM custom_variable_value WHERE id = ?", VALUE_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按变量和值查询（用于批量导入时的重复检测）
     */
    public CustomVariableValue findValueByVariableIdAndValue(String variableId, String value) {
        List<CustomVariableValue> list = jdbcTemplate.query(
                "SELECT * FROM custom_variable_value WHERE variable_id = ? AND value = ?",
                VALUE_MAPPER, variableId, value);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 统计变量下的值数量（硬性护栏用）
     */
    public long countValuesByVariableId(String variableId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_variable_value WHERE variable_id = ?",
                Long.class, variableId);
        return c != null ? c : 0L;
    }

    /**
     * 插入单条值
     */
    public void insertValue(CustomVariableValue v) {
        jdbcTemplate.update(
                "INSERT INTO custom_variable_value (id, variable_id, value, description, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?)",
                v.getId(), v.getVariableId(), v.getValue(), v.getDescription(), v.getSortOrder());
    }

    /**
     * 批量插入值（单事务）
     */
    @Transactional
    public void batchInsertValues(final List<CustomVariableValue> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO custom_variable_value (id, variable_id, value, description, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        CustomVariableValue v = values.get(i);
                        ps.setString(1, v.getId());
                        ps.setString(2, v.getVariableId());
                        ps.setString(3, v.getValue());
                        ps.setString(4, v.getDescription());
                        ps.setInt(5, v.getSortOrder());
                    }

                    @Override
                    public int getBatchSize() {
                        return values.size();
                    }
                });
    }

    /**
     * 更新值
     */
    public void updateValue(CustomVariableValue v) {
        jdbcTemplate.update(
                "UPDATE custom_variable_value SET value = ?, description = ?, sort_order = ? WHERE id = ?",
                v.getValue(), v.getDescription(), v.getSortOrder(), v.getId());
    }

    /**
     * 删除值（同时清理 group_value 关联）
     */
    @Transactional
    public void deleteValueCascade(String valueId) {
        jdbcTemplate.update(
                "DELETE FROM custom_variable_group_value WHERE value_id = ?", valueId);
        jdbcTemplate.update(
                "DELETE FROM custom_variable_value WHERE id = ?", valueId);
    }

    // ========== 分组 ==========

    /**
     * 查询变量下所有分组
     */
    public List<CustomVariableGroup> findGroupsByVariableId(String variableId) {
        return jdbcTemplate.query(
                "SELECT * FROM custom_variable_group WHERE variable_id = ? ORDER BY name",
                GROUP_MAPPER, variableId);
    }

    /**
     * 按 ID 查询分组
     */
    public CustomVariableGroup findGroupById(String id) {
        List<CustomVariableGroup> list = jdbcTemplate.query(
                "SELECT * FROM custom_variable_group WHERE id = ?", GROUP_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按变量和分组名查询（用于唯一性校验）
     */
    public CustomVariableGroup findGroupByVariableIdAndName(String variableId, String name) {
        List<CustomVariableGroup> list = jdbcTemplate.query(
                "SELECT * FROM custom_variable_group WHERE variable_id = ? AND name = ?",
                GROUP_MAPPER, variableId, name);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 统计变量下的分组数量（硬性护栏用）
     */
    public long countGroupsByVariableId(String variableId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_variable_group WHERE variable_id = ?",
                Long.class, variableId);
        return c != null ? c : 0L;
    }

    /**
     * 插入分组
     */
    public void insertGroup(CustomVariableGroup g) {
        jdbcTemplate.update(
                "INSERT INTO custom_variable_group (id, variable_id, name, description) VALUES (?, ?, ?, ?)",
                g.getId(), g.getVariableId(), g.getName(), g.getDescription());
    }

    /**
     * 更新分组（改名、改描述）
     */
    public void updateGroup(CustomVariableGroup g) {
        jdbcTemplate.update(
                "UPDATE custom_variable_group SET name = ?, description = ? WHERE id = ?",
                g.getName(), g.getDescription(), g.getId());
    }

    /**
     * 删除分组（同时清理 group_value 关联）
     */
    @Transactional
    public void deleteGroupCascade(String groupId) {
        jdbcTemplate.update(
                "DELETE FROM custom_variable_group_value WHERE group_id = ?", groupId);
        jdbcTemplate.update(
                "DELETE FROM custom_variable_group WHERE id = ?", groupId);
    }

    // ========== 分组-值 关联 ==========

    /**
     * 查询分组的所有值 ID
     */
    public List<String> findValueIdsByGroupId(String groupId) {
        return jdbcTemplate.queryForList(
                "SELECT value_id FROM custom_variable_group_value WHERE group_id = ?",
                String.class, groupId);
    }

    /**
     * 一次性拉取变量下所有分组的值关联，返回 groupId → valueIds 映射
     * <p>
     * 用于 Service 层构建聚合 DTO 和内存缓存，避免 N+1 查询。
     */
    public Map<String, List<String>> findGroupValueIdsByVariableId(String variableId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT gv.group_id, gv.value_id FROM custom_variable_group_value gv " +
                        "JOIN custom_variable_group g ON gv.group_id = g.id " +
                        "WHERE g.variable_id = ?",
                variableId);
        Map<String, List<String>> result = new HashMap<String, List<String>>();
        for (Map<String, Object> row : rows) {
            String groupId = (String) row.get("group_id");
            String valueId = (String) row.get("value_id");
            List<String> list = result.get(groupId);
            if (list == null) {
                list = new ArrayList<String>();
                result.put(groupId, list);
            }
            list.add(valueId);
        }
        return result;
    }

    /**
     * 替换分组的全部值关联（先删后插，单事务）
     * <p>
     * 编辑分组时调用：前端传入勾选后的 valueIds 数组，后端先清空旧关联再插入新关联。
     */
    @Transactional
    public void replaceGroupValues(final String groupId, final List<String> valueIds) {
        jdbcTemplate.update(
                "DELETE FROM custom_variable_group_value WHERE group_id = ?", groupId);
        if (valueIds == null || valueIds.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO custom_variable_group_value (group_id, value_id) VALUES (?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, groupId);
                        ps.setString(2, valueIds.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return valueIds.size();
                    }
                });
    }
}
