package com.mockhub.mock.model.dto.variable;

/**
 * 批量新增候选值的响应体
 */
public class BatchValuesResult {

    /** 成功插入的条数 */
    private int inserted;

    /** 因已存在或为空而跳过的条数 */
    private int skipped;

    public BatchValuesResult() {
    }

    public BatchValuesResult(int inserted, int skipped) {
        this.inserted = inserted;
        this.skipped = skipped;
    }

    public int getInserted() { return inserted; }
    public void setInserted(int inserted) { this.inserted = inserted; }

    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
}
