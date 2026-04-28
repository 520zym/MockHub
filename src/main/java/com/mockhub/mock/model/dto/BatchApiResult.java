package com.mockhub.mock.model.dto;

/**
 * 批量操作结果。
 * <p>
 * affected 表示实际处理的接口数量；忽略的（如不存在的 ID）不计入。
 */
public class BatchApiResult {

    private int affected;

    public BatchApiResult() {
    }

    public BatchApiResult(int affected) {
        this.affected = affected;
    }

    public int getAffected() {
        return affected;
    }

    public void setAffected(int affected) {
        this.affected = affected;
    }
}
