package com.mockhub.stats.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.stats.model.dto.StatsOverviewVO;
import com.mockhub.stats.model.dto.TopApiVO;
import com.mockhub.stats.model.dto.TrendPointVO;
import com.mockhub.stats.model.dto.ZombieApiVO;
import com.mockhub.stats.repository.StatsRepository;
import com.mockhub.system.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现。
 * <p>
 * 权限规则：
 * <ul>
 *   <li>超管：teamId 任意（含 null=全部团队）</li>
 *   <li>团队管理员：仅可看自己 role=TEAM_ADMIN 的团队，必须传具体 teamId</li>
 *   <li>其他（含普通成员）：所有方法 403</li>
 * </ul>
 *
 * 时间窗口处理：days 参数为正整数；起始时间 = 当前日期减去 days 天的 00:00:00；
 * 上限不显式做（由 SQL 查询当下时刻自然兜底）。
 */
@Service
public class StatsServiceImpl implements StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsServiceImpl.class);

    /** 单次查询返回的最大数据量上限（防恶意 limit） */
    private static final int MAX_LIMIT = 100;

    /** 窗口最大天数（防恶意 days） */
    private static final int MAX_DAYS = 365;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StatsRepository statsRepository;
    private final UserRepository userRepository;

    public StatsServiceImpl(StatsRepository statsRepository, UserRepository userRepository) {
        this.statsRepository = statsRepository;
        this.userRepository = userRepository;
    }

    @Override
    public StatsOverviewVO getOverview(String teamId, int days) {
        String resolvedTeamId = checkAccess(teamId);
        int safeDays = clampDays(days);

        String windowStart = startOfDayIsoMinusDays(safeDays);
        String todayStart = startOfDayIsoMinusDays(0);

        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setEnabledApiCount(statsRepository.countEnabledApis(resolvedTeamId));
        vo.setTodayCallCount(statsRepository.countCalls(resolvedTeamId, todayStart));
        vo.setWindowCallCount(statsRepository.countCalls(resolvedTeamId, windowStart));
        vo.setAvgDurationMs(statsRepository.avgDurationMs(resolvedTeamId, windowStart));
        return vo;
    }

    @Override
    public List<TrendPointVO> getTrend(String teamId, int days) {
        String resolvedTeamId = checkAccess(teamId);
        int safeDays = clampDays(days);

        String windowStart = startOfDayIsoMinusDays(safeDays - 1);
        // 注：safeDays - 1 是"含今天的最近 N 天"。例如 days=7 → 从 6 天前 00:00 起，共 7 个自然日。

        List<TrendPointVO> sparse = statsRepository.aggregateDailyCalls(resolvedTeamId, windowStart);
        return fillMissingDays(sparse, safeDays);
    }

    @Override
    public List<TopApiVO> getTopApis(String teamId, int days, int limit) {
        String resolvedTeamId = checkAccess(teamId);
        int safeDays = clampDays(days);
        int safeLimit = clampLimit(limit);

        String windowStart = startOfDayIsoMinusDays(safeDays);
        return statsRepository.topApis(resolvedTeamId, windowStart, safeLimit);
    }

    @Override
    public List<ZombieApiVO> getZombies(String teamId, int days, int limit) {
        String resolvedTeamId = checkAccess(teamId);
        int safeDays = clampDays(days);
        int safeLimit = clampLimit(limit);

        String threshold = startOfDayIsoMinusDays(safeDays);
        return statsRepository.zombieApis(resolvedTeamId, threshold, safeLimit);
    }

    @Override
    public long countZombies(String teamId, int days) {
        String resolvedTeamId = checkAccess(teamId);
        int safeDays = clampDays(days);
        String threshold = startOfDayIsoMinusDays(safeDays);
        return statsRepository.countZombieApis(resolvedTeamId, threshold);
    }

    // ==================== 权限与参数校验 ====================

    /**
     * 权限校验并返回有效的 teamId（null 表示跨团队聚合）。
     * <p>
     * - 超管：teamId 原样返回（含 null）；
     * - 非超管：必须传 teamId，且必须是该团队的 TEAM_ADMIN，否则 403；
     * - 普通成员（在该团队仅 MEMBER 角色）：403。
     */
    private String checkAccess(String teamId) {
        boolean isSuperAdmin = SecurityContextUtil.isSuperAdmin();
        boolean teamIdEmpty = teamId == null || teamId.isEmpty();

        if (isSuperAdmin) {
            return teamIdEmpty ? null : teamId;
        }

        if (teamIdEmpty) {
            log.warn("非超管尝试查跨团队统计：userId={}", SecurityContextUtil.getCurrentUserId());
            throw new BizException(40103, "请指定团队");
        }

        String userId = SecurityContextUtil.getCurrentUserId();
        String role = userRepository.findUserTeamRole(userId, teamId);
        if (!"TEAM_ADMIN".equals(role)) {
            log.warn("非团队管理员查询统计：userId={}, teamId={}, role={}", userId, teamId, role);
            throw new BizException(40101, "仅超级管理员或团队管理员可查看统计");
        }
        return teamId;
    }

    private int clampDays(int days) {
        if (days <= 0) {
            return 7;
        }
        return Math.min(days, MAX_DAYS);
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    // ==================== 时间工具 ====================

    /**
     * 返回 (今天 - n) 当日 00:00:00 的 ISO 时间字符串。n=0 表示今天 00:00。
     */
    private String startOfDayIsoMinusDays(int n) {
        return LocalDate.now().minusDays(n).format(DATE) + "T00:00:00";
    }

    /**
     * 把稀疏的每日聚合补齐成连续 days 天，缺失日填 0。
     * 数据库返回的 day 字符串就是 yyyy-MM-dd，与生成的 key 一致。
     */
    private List<TrendPointVO> fillMissingDays(List<TrendPointVO> sparse, int days) {
        Map<String, Long> map = new HashMap<String, Long>();
        for (TrendPointVO p : sparse) {
            map.put(p.getDate(), p.getCount());
        }
        List<TrendPointVO> out = new ArrayList<TrendPointVO>(days);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String day = today.minusDays(i).format(DATE);
            Long cnt = map.get(day);
            out.add(new TrendPointVO(day, cnt == null ? 0L : cnt));
        }
        return out;
    }
}
