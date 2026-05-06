package com.mockhub.stats.service;

import com.mockhub.stats.model.dto.StatsOverviewVO;
import com.mockhub.stats.model.dto.TopApiVO;
import com.mockhub.stats.model.dto.TrendPointVO;
import com.mockhub.stats.model.dto.ZombieApiVO;

import java.util.List;

/**
 * 统计服务接口。
 * <p>
 * 提供统计页面所需的 4 类数据：KPI 总览、调用趋势、TOP-N 热度、僵尸接口。
 * 所有方法在内部完成权限校验：超管可看任意 teamId（含 null 全部团队），团队管理员
 * 仅可看自己 role=TEAM_ADMIN 的团队，其他用户被拒绝（BizException 40101/40103）。
 */
public interface StatsService {

    /** KPI 总览：启用接口数、今日调用、窗口内总调用、平均响应耗时 */
    StatsOverviewVO getOverview(String teamId, int days);

    /** 窗口内每日调用次数（连续日期，缺失天填 0） */
    List<TrendPointVO> getTrend(String teamId, int days);

    /** 接口热度 TOP-N */
    List<TopApiVO> getTopApis(String teamId, int days, int limit);

    /** 僵尸接口列表（last_called_at 早于 N 天前或从未调用） */
    List<ZombieApiVO> getZombies(String teamId, int days, int limit);

    /** 僵尸接口总数（用于卡片标题角标） */
    long countZombies(String teamId, int days);
}
