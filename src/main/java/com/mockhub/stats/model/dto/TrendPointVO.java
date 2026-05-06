package com.mockhub.stats.model.dto;

/**
 * 调用趋势折线图的单日数据点。
 * <p>
 * Service 层会补齐窗口内每一天（包括 0 调用日），保证前端折线图 x 轴连续。
 */
public class TrendPointVO {

    /** 自然日（yyyy-MM-dd 格式） */
    private String date;

    /** 当日调用次数 */
    private long count;

    public TrendPointVO() {
    }

    public TrendPointVO(String date, long count) {
        this.date = date;
        this.count = count;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
