package com.mockhub.mock.model.dto.variable;

import java.util.List;

/**
 * 批量新增候选值的请求体
 * <p>
 * 前端在维护页「批量粘贴」对话框中将多行文本解析为 {@code List<ValueRequest>}
 * 再 POST 到后端，后端不做文本层面的拆分。重复的值会被跳过并统计到响应的 skipped 字段。
 */
public class BatchValuesRequest {

    /** 候选值列表 */
    private List<ValueRequest> values;

    public List<ValueRequest> getValues() { return values; }
    public void setValues(List<ValueRequest> values) { this.values = values; }
}
