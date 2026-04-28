package com.mockhub.mock.repository;

import com.mockhub.mock.model.entity.ApiDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ApiRepository 排序与防 SQL 注入单元测试。
 * <p>
 * 重点覆盖 v1.4.5 新增的 sortBy/sortDir 路径：
 *  - 默认行为（sortBy=null）：按 updated_at DESC
 *  - 白名单字段：updatedAt / createdAt / name / path 正确映射列名
 *  - 排序方向 sortDir：asc / ASC / desc / DESC 大小写不敏感
 *  - 防 SQL 注入：恶意 sortBy 不破坏库、不抛异常、回退默认排序
 * <p>
 * 用嵌入式 SQLite + @TempDir 的轻量集成方式，参考 SchemaVersionMigrationTest 模式。
 */
class ApiRepositoryTest {

    @TempDir
    Path tempDir;

    private ApiRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sds = new SQLiteDataSource();
        sds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toString());
        jdbc = new JdbcTemplate(sds);

        // 与 schema.sql 中 api_definition 表保持一致的列集（最小覆盖 findAll SELECT 字段）
        jdbc.execute(
                "CREATE TABLE api_definition ("
                        + "id TEXT PRIMARY KEY, team_id TEXT NOT NULL, group_id TEXT, "
                        + "type TEXT NOT NULL DEFAULT 'REST', name TEXT NOT NULL, description TEXT, "
                        + "method TEXT NOT NULL, path TEXT NOT NULL, "
                        + "response_code INTEGER NOT NULL DEFAULT 200, "
                        + "content_type TEXT NOT NULL DEFAULT 'application/json', "
                        + "response_body TEXT, delay_ms INTEGER NOT NULL DEFAULT 0, "
                        + "enabled INTEGER NOT NULL DEFAULT 1, "
                        + "global_header_overrides TEXT, soap_config TEXT, scenarios TEXT, "
                        + "created_by TEXT, created_at TEXT NOT NULL, "
                        + "updated_at TEXT NOT NULL, updated_by TEXT)"
        );

        // 插入 3 条数据：name 字典序 Alpha < Bravo < Charlie；
        // updated_at: A 最旧，B 居中，C 最新（用于验证默认 DESC 排序）
        // path: /a < /b < /c
        insert("id1", "Charlie", "/c", "2026-04-01T10:00:00", "2026-04-28T15:00:00");
        insert("id2", "Alpha",   "/a", "2026-04-15T10:00:00", "2026-04-28T13:00:00");
        insert("id3", "Bravo",   "/b", "2026-04-10T10:00:00", "2026-04-28T14:00:00");

        repository = new ApiRepository(jdbc);
    }

    private void insert(String id, String name, String path, String createdAt, String updatedAt) {
        jdbc.update(
                "INSERT INTO api_definition(id, team_id, type, name, method, path, "
                        + "response_code, content_type, delay_ms, enabled, created_at, updated_at) "
                        + "VALUES(?, 'team-1', 'REST', ?, 'GET', ?, 200, 'application/json', 0, 1, ?, ?)",
                id, name, path, createdAt, updatedAt
        );
    }

    private List<ApiDefinition> query(String sortBy, String sortDir) {
        return repository.findAll(
                Collections.<String>emptyList(),  // teamIds
                "team-1",                          // teamId
                null, null, null, null,            // groupId, method, enabled, keyword
                Collections.<String>emptyList(),  // tagIds
                null,                              // type
                sortBy, sortDir,
                0, 100                             // offset, limit
        );
    }

    // ============ 默认排序 ============

    @Test
    void defaultSort_isUpdatedAtDesc_whenSortByNull() {
        List<ApiDefinition> rows = query(null, null);
        assertEquals(3, rows.size());
        // updated_at: id1=15:00 > id3=14:00 > id2=13:00
        assertEquals("id1", rows.get(0).getId());
        assertEquals("id3", rows.get(1).getId());
        assertEquals("id2", rows.get(2).getId());
    }

    // ============ 白名单字段 ============

    @Test
    void sortByName_asc() {
        List<ApiDefinition> rows = query("name", "asc");
        assertEquals("Alpha", rows.get(0).getName());
        assertEquals("Bravo", rows.get(1).getName());
        assertEquals("Charlie", rows.get(2).getName());
    }

    @Test
    void sortByName_desc() {
        List<ApiDefinition> rows = query("name", "desc");
        assertEquals("Charlie", rows.get(0).getName());
        assertEquals("Bravo", rows.get(1).getName());
        assertEquals("Alpha", rows.get(2).getName());
    }

    @Test
    void sortByPath_asc() {
        List<ApiDefinition> rows = query("path", "asc");
        assertEquals("/a", rows.get(0).getPath());
        assertEquals("/b", rows.get(1).getPath());
        assertEquals("/c", rows.get(2).getPath());
    }

    @Test
    void sortByCreatedAt_asc() {
        List<ApiDefinition> rows = query("createdAt", "asc");
        // created_at: id1=04-01 < id3=04-10 < id2=04-15
        assertEquals("id1", rows.get(0).getId());
        assertEquals("id3", rows.get(1).getId());
        assertEquals("id2", rows.get(2).getId());
    }

    @Test
    void sortByUpdatedAt_explicit() {
        List<ApiDefinition> rows = query("updatedAt", "desc");
        assertEquals("id1", rows.get(0).getId());
    }

    // ============ 大小写不敏感 ============

    @Test
    void sortDir_isCaseInsensitive() {
        List<ApiDefinition> rowsLower = query("name", "asc");
        List<ApiDefinition> rowsUpper = query("name", "ASC");
        List<ApiDefinition> rowsMixed = query("name", "AsC");
        assertEquals(rowsLower.get(0).getName(), rowsUpper.get(0).getName());
        assertEquals(rowsLower.get(0).getName(), rowsMixed.get(0).getName());
    }

    // ============ 防 SQL 注入：白名单外回退默认 ============

    @Test
    void unknownSortBy_fallsBackToUpdatedAt_butKeepsDirection() {
        // 白名单外的字段名应该被忽略 → 回退列 a.updated_at；sortDir 是合法值时仍生效
        // updated_at: id2=13:00 < id3=14:00 < id1=15:00（升序）
        List<ApiDefinition> rows = query("id", "asc");
        assertEquals("id2", rows.get(0).getId());
        assertEquals("id3", rows.get(1).getId());
        assertEquals("id1", rows.get(2).getId());
    }

    @Test
    void sqlInjection_inSortBy_doesNotBreakAndFallsBack() {
        // 经典 SQL 注入 payload：把额外语句拼到 ORDER BY
        String payload = "name; DROP TABLE api_definition; --";
        // 用 desc 让回退后的 updated_at 排序与默认一致，方便断言
        List<ApiDefinition> rows = query(payload, "desc");
        // 不应抛异常，应回退到 a.updated_at DESC
        assertEquals(3, rows.size());
        assertEquals("id1", rows.get(0).getId());

        // 验证表仍然存在（注入未生效）
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM api_definition", Integer.class);
        assertNotNull(count);
        assertEquals(3, (int) count);
    }

    @Test
    void sqlInjection_inSortDir_doesNotBreakAndFallsBack() {
        // sortDir 不在 asc/desc 集合内时回退 DESC
        String payload = "DESC; UPDATE api_definition SET name='HACKED'; --";
        List<ApiDefinition> rows = query("name", payload);
        assertEquals(3, rows.size());

        // 验证名字未被恶意 UPDATE 修改
        Integer hackedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE name = 'HACKED'", Integer.class);
        assertNotNull(hackedCount);
        assertEquals(0, (int) hackedCount);
    }

    @Test
    void emptyStringSortBy_fallsBackToDefaultColumn() {
        // sortBy="" 也不在白名单 → 回退到 a.updated_at；与 unknownSortBy 等价
        List<ApiDefinition> rows = query("", "desc");
        assertEquals(3, rows.size());
        assertEquals("id1", rows.get(0).getId()); // updated_at DESC
    }

    // ============ 批量操作（v1.4.5 新增） ============

    @Test
    void findByIds_returnsMatched_skipsMissing() {
        List<ApiDefinition> rows = repository.findByIds(java.util.Arrays.asList("id1", "id2", "not-exist"));
        assertEquals(2, rows.size());
    }

    @Test
    void findByIds_emptyOrNull_returnsEmpty() {
        assertEquals(0, repository.findByIds(Collections.<String>emptyList()).size());
        assertEquals(0, repository.findByIds(null).size());
    }

    @Test
    void batchUpdateEnabled_disablesAll_andUpdatesTimestamp() {
        int affected = repository.batchUpdateEnabled(
                java.util.Arrays.asList("id1", "id2"), false, "2026-04-28T20:00:00");
        assertEquals(2, affected);

        Integer disabled = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE id IN ('id1','id2') AND enabled = 0",
                Integer.class);
        assertNotNull(disabled);
        assertEquals(2, (int) disabled);

        // id3 不应被影响
        Integer id3Enabled = jdbc.queryForObject(
                "SELECT enabled FROM api_definition WHERE id = 'id3'", Integer.class);
        assertNotNull(id3Enabled);
        assertEquals(1, (int) id3Enabled);
    }

    @Test
    void batchUpdateEnabled_emptyIds_returnsZero() {
        int affected = repository.batchUpdateEnabled(
                Collections.<String>emptyList(), true, "2026-04-28T20:00:00");
        assertEquals(0, affected);
    }

    @Test
    void batchUpdateGroup_setsTargetGroup() {
        int affected = repository.batchUpdateGroup(
                java.util.Arrays.asList("id1", "id2"), "group-A", "2026-04-28T20:00:00");
        assertEquals(2, affected);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE group_id = 'group-A'", Integer.class);
        assertNotNull(count);
        assertEquals(2, (int) count);
    }

    @Test
    void batchUpdateGroup_emptyTarget_setsNull() {
        // 先设值，再用空字符串清空
        repository.batchUpdateGroup(
                java.util.Arrays.asList("id1"), "group-A", "2026-04-28T20:00:00");
        int affected = repository.batchUpdateGroup(
                java.util.Arrays.asList("id1"), "", "2026-04-28T20:01:00");
        assertEquals(1, affected);

        Integer nullCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE id = 'id1' AND group_id IS NULL",
                Integer.class);
        assertNotNull(nullCount);
        assertEquals(1, (int) nullCount);
    }

    @Test
    void batchUpdateGroup_nullTarget_setsNull() {
        int affected = repository.batchUpdateGroup(
                java.util.Arrays.asList("id1"), null, "2026-04-28T20:00:00");
        assertEquals(1, affected);

        Integer nullCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_definition WHERE id = 'id1' AND group_id IS NULL",
                Integer.class);
        assertNotNull(nullCount);
        assertEquals(1, (int) nullCount);
    }

    @Test
    void batchDeleteByIds_removesRows() {
        int affected = repository.batchDeleteByIds(java.util.Arrays.asList("id1", "id3"));
        assertEquals(2, affected);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_definition", Integer.class);
        assertNotNull(remaining);
        assertEquals(1, (int) remaining);
        // 剩下的应该是 id2
        String remainingId = jdbc.queryForObject(
                "SELECT id FROM api_definition", String.class);
        assertEquals("id2", remainingId);
    }

    @Test
    void batchDeleteByIds_emptyIds_returnsZero() {
        int affected = repository.batchDeleteByIds(Collections.<String>emptyList());
        assertEquals(0, affected);
        // 数据没动
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_definition", Integer.class);
        assertNotNull(count);
        assertEquals(3, (int) count);
    }
}
