package com.mockhub.mock.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 接口-标签关联数据访问层
 * <p>
 * 使用 JdbcTemplate 操作 api_tag 表。
 */
@Repository
public class ApiTagRepository {

    private static final Logger log = LoggerFactory.getLogger(ApiTagRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ApiTagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询接口关联的标签 ID 列表
     *
     * @param apiId 接口 ID
     * @return 标签 ID 列表
     */
    public List<String> findTagIdsByApiId(String apiId) {
        return jdbcTemplate.queryForList(
                "SELECT tag_id FROM api_tag WHERE api_id = ?", String.class, apiId);
    }

    /**
     * 整体替换接口的标签关联（先删后插）
     *
     * @param apiId  接口 ID
     * @param tagIds 新的标签 ID 列表
     */
    public void replaceTagsForApi(String apiId, List<String> tagIds) {
        // 先删除旧关联
        jdbcTemplate.update("DELETE FROM api_tag WHERE api_id = ?", apiId);

        // 再批量插入新关联
        if (tagIds != null && !tagIds.isEmpty()) {
            for (String tagId : tagIds) {
                jdbcTemplate.update(
                        "INSERT INTO api_tag (api_id, tag_id) VALUES (?, ?)", apiId, tagId);
            }
        }
    }

    /**
     * 删除接口的所有标签关联（删除接口时调用）
     *
     * @param apiId 接口 ID
     */
    public void deleteByApiId(String apiId) {
        jdbcTemplate.update("DELETE FROM api_tag WHERE api_id = ?", apiId);
    }

    /**
     * 删除标签的所有关联（删除标签时调用）
     *
     * @param tagId 标签 ID
     */
    public void deleteByTagId(String tagId) {
        jdbcTemplate.update("DELETE FROM api_tag WHERE tag_id = ?", tagId);
    }
}
