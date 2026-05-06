package com.mockhub.stats.controller;

import com.mockhub.common.model.Result;
import com.mockhub.stats.model.dto.StatsOverviewVO;
import com.mockhub.stats.model.dto.TopApiVO;
import com.mockhub.stats.model.dto.TrendPointVO;
import com.mockhub.stats.model.dto.ZombieApiVO;
import com.mockhub.stats.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计接口 Controller。
 * <p>
 * 端点列表：
 * <ul>
 *   <li>GET /api/stats/overview?teamId=&days=7 — KPI 总览</li>
 *   <li>GET /api/stats/trend?teamId=&days=7 — 调用趋势</li>
 *   <li>GET /api/stats/top-apis?teamId=&days=7&limit=10 — 接口热度 TOP-N</li>
 *   <li>GET /api/stats/zombies?teamId=&days=30&limit=20 — 僵尸接口列表（含总数）</li>
 * </ul>
 *
 * teamId 留空仅超管允许（跨团队聚合）；其他用户必传具体 teamId 且需是该团队管理员。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * KPI 总览：启用接口数、今日调用、窗口内总调用、平均响应耗时。
     *
     * @param teamId 团队 ID（可空，仅超管可空）
     * @param days   时间窗口（默认 7，最大 365）
     * @return 总览数据
     */
    @GetMapping("/overview")
    public Result<StatsOverviewVO> getOverview(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false, defaultValue = "7") int days) {
        log.debug("GET /api/stats/overview teamId={} days={}", teamId, days);
        return Result.ok(statsService.getOverview(teamId, days));
    }

    /**
     * 调用趋势：返回连续 days 天的每日调用次数（缺失日填 0）。
     *
     * @param teamId 团队 ID（可空）
     * @param days   时间窗口（默认 7）
     * @return 趋势数据点列表（按日期升序）
     */
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> getTrend(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false, defaultValue = "7") int days) {
        log.debug("GET /api/stats/trend teamId={} days={}", teamId, days);
        return Result.ok(statsService.getTrend(teamId, days));
    }

    /**
     * 接口热度 TOP-N。
     *
     * @param teamId 团队 ID（可空）
     * @param days   时间窗口（默认 7）
     * @param limit  返回数量（默认 10，最大 100）
     * @return 调用次数降序的接口列表
     */
    @GetMapping("/top-apis")
    public Result<List<TopApiVO>> getTopApis(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false, defaultValue = "7") int days,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        log.debug("GET /api/stats/top-apis teamId={} days={} limit={}", teamId, days, limit);
        return Result.ok(statsService.getTopApis(teamId, days, limit));
    }

    /**
     * 僵尸接口列表（last_called_at 早于 days 天前或从未调用）。
     * 同时返回总数，前端用于卡片标题展示"X 个"。
     *
     * @param teamId 团队 ID（可空）
     * @param days   阈值天数（默认 30）
     * @param limit  返回数量（默认 20，最大 100）
     * @return Map 包含 list（接口列表）和 total（总数）
     */
    @GetMapping("/zombies")
    public Result<Map<String, Object>> getZombies(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false, defaultValue = "30") int days,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        log.debug("GET /api/stats/zombies teamId={} days={} limit={}", teamId, days, limit);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("list", statsService.getZombies(teamId, days, limit));
        data.put("total", statsService.countZombies(teamId, days));
        return Result.ok(data);
    }
}
