package com.mockhub.stats.repository;

import com.mockhub.stats.model.dto.TopApiVO;
import com.mockhub.stats.model.dto.TrendPointVO;
import com.mockhub.stats.model.dto.ZombieApiVO;
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
 * 统计数据访问层。
 * <p>
 * 主要基于 request_log 表实时聚合（受日志清理窗口限制）；僵尸接口查询基于
 * api_definition.last_called_at（永久累计，不受日志清理影响）。
 * <p>
 * 所有 SQL 都用 ? 占位符；teamId 为 null 时跨团队查询（仅超管允许，权限检查
 * 在 Service 层完成）。
 */
@Repository
public class StatsRepository {

    private static final Logger log = LoggerFactory.getLogger(StatsRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public StatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计当前过滤范围内启用的接口数量。
     *
     * @param teamId 团队 ID，null 表示跨团队
     * @return 启用接口数
     */
    public long countEnabledApis(String teamId) {
        String sql = "SELECT COUNT(*) FROM api_definition WHERE enabled = 1";
        Long count;
        if (teamId == null) {
            count = jdbcTemplate.queryForObject(sql, Long.class);
        } else {
            count = jdbcTemplate.queryForObject(sql + " AND team_id = ?", Long.class, teamId);
        }
        return count != null ? count : 0;
    }

    /**
     * 统计 created_at &gt;= startInclusive 的请求日志条数（窗口内调用次数）。
     *
     * @param teamId         团队 ID，null 表示跨团队
     * @param startInclusive 起始时间（ISO 格式，含），如 "2026-04-29T00:00:00"
     * @return 调用次数
     */
    public long countCalls(String teamId, String startInclusive) {
        String sql = "SELECT COUNT(*) FROM request_log WHERE created_at >= ?";
        Long count;
        if (teamId == null) {
            count = jdbcTemplate.queryForObject(sql, Long.class, startInclusive);
        } else {
            count = jdbcTemplate.queryForObject(sql + " AND team_id = ?",
                    Long.class, startInclusive, teamId);
        }
        return count != null ? count : 0;
    }

    /**
     * 计算窗口内平均响应耗时（毫秒）。无数据返回 0。
     *
     * @param teamId         团队 ID，null 表示跨团队
     * @param startInclusive 起始时间（ISO 格式）
     * @return 平均耗时（毫秒，向下取整）
     */
    public long avgDurationMs(String teamId, String startInclusive) {
        String sql = "SELECT AVG(duration_ms) FROM request_log WHERE created_at >= ?";
        Double avg;
        if (teamId == null) {
            avg = jdbcTemplate.queryForObject(sql, Double.class, startInclusive);
        } else {
            avg = jdbcTemplate.queryForObject(sql + " AND team_id = ?",
                    Double.class, startInclusive, teamId);
        }
        return avg != null ? avg.longValue() : 0;
    }

    /**
     * 按自然日聚合调用次数，返回稀疏列表（仅含有调用的天）。
     * Service 层负责补齐空白天为 0。
     *
     * @param teamId         团队 ID，null 表示跨团队
     * @param startInclusive 起始时间（ISO 格式）
     * @return 按 date 升序排序的稀疏数据点列表
     */
    public List<TrendPointVO> aggregateDailyCalls(String teamId, String startInclusive) {
        // SQLite 的 substr(created_at, 1, 10) 截取 yyyy-MM-dd
        StringBuilder sql = new StringBuilder(
                "SELECT substr(created_at, 1, 10) AS day, COUNT(*) AS cnt " +
                "FROM request_log WHERE created_at >= ? ");
        List<Object> args = new ArrayList<Object>();
        args.add(startInclusive);
        if (teamId != null) {
            sql.append("AND team_id = ? ");
            args.add(teamId);
        }
        sql.append("GROUP BY day ORDER BY day ASC");

        return jdbcTemplate.query(sql.toString(), args.toArray(), new RowMapper<TrendPointVO>() {
            @Override
            public TrendPointVO mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new TrendPointVO(rs.getString("day"), rs.getLong("cnt"));
            }
        });
    }

    /**
     * 接口热度 TOP-N。
     * <p>
     * 接口被删除后 LEFT JOIN 结果对应字段为 null，但 callCount 仍准确反映日志聚合。
     *
     * @param teamId         团队 ID，null 表示跨团队
     * @param startInclusive 起始时间（ISO 格式）
     * @param limit          返回数量上限
     * @return 调用次数降序的接口列表
     */
    public List<TopApiVO> topApis(String teamId, String startInclusive, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT rl.api_id AS api_id, " +
                "       ad.team_id AS team_id, " +
                "       t.identifier AS team_identifier, " +
                "       ad.method AS method, " +
                "       ad.path AS path, " +
                "       ad.name AS name, " +
                "       COUNT(*) AS cnt " +
                "FROM request_log rl " +
                "LEFT JOIN api_definition ad ON ad.id = rl.api_id " +
                "LEFT JOIN team t ON t.id = ad.team_id " +
                "WHERE rl.created_at >= ? AND rl.api_id IS NOT NULL ");
        List<Object> args = new ArrayList<Object>();
        args.add(startInclusive);
        if (teamId != null) {
            sql.append("AND rl.team_id = ? ");
            args.add(teamId);
        }
        sql.append("GROUP BY rl.api_id ORDER BY cnt DESC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), args.toArray(), new RowMapper<TopApiVO>() {
            @Override
            public TopApiVO mapRow(ResultSet rs, int rowNum) throws SQLException {
                TopApiVO vo = new TopApiVO();
                vo.setApiId(rs.getString("api_id"));
                vo.setTeamId(rs.getString("team_id"));
                vo.setTeamIdentifier(rs.getString("team_identifier"));
                vo.setMethod(rs.getString("method"));
                vo.setPath(rs.getString("path"));
                vo.setName(rs.getString("name"));
                vo.setCallCount(rs.getLong("cnt"));
                return vo;
            }
        });
    }

    /**
     * 僵尸接口列表：last_called_at 早于阈值或为 null（从未调用过）。
     * <p>
     * 只看启用状态的接口（禁用的接口"僵尸"性质不重要，且会污染列表）。
     * 排序：从未调用 优先于 已调用太久；从未调用之间按 created_at 升序；
     * 已调用之间按 last_called_at 升序。
     *
     * @param teamId    团队 ID，null 表示跨团队
     * @param threshold 阈值（ISO 格式），last_called_at 早于此值视为僵尸
     * @param limit     返回数量上限
     * @return 僵尸接口列表
     */
    public List<ZombieApiVO> zombieApis(String teamId, String threshold, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT ad.id AS api_id, " +
                "       ad.team_id AS team_id, " +
                "       t.identifier AS team_identifier, " +
                "       ad.method AS method, " +
                "       ad.path AS path, " +
                "       ad.name AS name, " +
                "       ad.last_called_at AS last_called_at, " +
                "       ad.hit_count AS hit_count " +
                "FROM api_definition ad " +
                "LEFT JOIN team t ON t.id = ad.team_id " +
                "WHERE ad.enabled = 1 " +
                "  AND (ad.last_called_at IS NULL OR ad.last_called_at < ?) ");
        List<Object> args = new ArrayList<Object>();
        args.add(threshold);
        if (teamId != null) {
            sql.append("AND ad.team_id = ? ");
            args.add(teamId);
        }
        sql.append("ORDER BY CASE WHEN ad.last_called_at IS NULL THEN 0 ELSE 1 END, " +
                "         ad.last_called_at ASC, " +
                "         ad.created_at ASC " +
                "LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), args.toArray(), new RowMapper<ZombieApiVO>() {
            @Override
            public ZombieApiVO mapRow(ResultSet rs, int rowNum) throws SQLException {
                ZombieApiVO vo = new ZombieApiVO();
                vo.setApiId(rs.getString("api_id"));
                vo.setTeamId(rs.getString("team_id"));
                vo.setTeamIdentifier(rs.getString("team_identifier"));
                vo.setMethod(rs.getString("method"));
                vo.setPath(rs.getString("path"));
                vo.setName(rs.getString("name"));
                vo.setLastCalledAt(rs.getString("last_called_at"));
                vo.setHitCount(rs.getLong("hit_count"));
                return vo;
            }
        });
    }

    /**
     * 统计当前过滤范围内的僵尸接口总数（用于在卡片标题展示"X 个"）。
     *
     * @param teamId    团队 ID，null 表示跨团队
     * @param threshold 阈值（ISO 格式）
     * @return 僵尸接口总数
     */
    public long countZombieApis(String teamId, String threshold) {
        String sql = "SELECT COUNT(*) FROM api_definition " +
                "WHERE enabled = 1 AND (last_called_at IS NULL OR last_called_at < ?)";
        Long count;
        if (teamId == null) {
            count = jdbcTemplate.queryForObject(sql, Long.class, threshold);
        } else {
            count = jdbcTemplate.queryForObject(sql + " AND team_id = ?",
                    Long.class, threshold, teamId);
        }
        return count != null ? count : 0;
    }
}
