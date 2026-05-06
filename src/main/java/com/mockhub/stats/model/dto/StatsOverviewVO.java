package com.mockhub.stats.model.dto;

/**
 * 统计页面顶部 KPI 总览数据。
 * <p>
 * 4 个核心指标：启用接口数、今日调用、窗口内总调用、窗口内平均响应耗时。
 * 计数指标全部基于 request_log 实时聚合，受日志清理窗口（默认 1000 条 / 30 天）影响；
 * 启用接口数基于 api_definition 实时统计，与日志窗口无关。
 */
public class StatsOverviewVO {

    /** 当前过滤范围内启用的接口数量 */
    private long enabledApiCount;

    /** 今日（自然日 00:00 起）调用次数 */
    private long todayCallCount;

    /** 窗口内（最近 N 天）总调用次数 */
    private long windowCallCount;

    /** 窗口内平均响应耗时（毫秒，无数据时为 0） */
    private long avgDurationMs;

    public StatsOverviewVO() {
    }

    public long getEnabledApiCount() {
        return enabledApiCount;
    }

    public void setEnabledApiCount(long enabledApiCount) {
        this.enabledApiCount = enabledApiCount;
    }

    public long getTodayCallCount() {
        return todayCallCount;
    }

    public void setTodayCallCount(long todayCallCount) {
        this.todayCallCount = todayCallCount;
    }

    public long getWindowCallCount() {
        return windowCallCount;
    }

    public void setWindowCallCount(long windowCallCount) {
        this.windowCallCount = windowCallCount;
    }

    public long getAvgDurationMs() {
        return avgDurationMs;
    }

    public void setAvgDurationMs(long avgDurationMs) {
        this.avgDurationMs = avgDurationMs;
    }
}
