package com.mockhub.common.model;

import java.util.List;

/**
 * 分页响应格式
 * {"total": 100, "page": 1, "size": 20, "items": [...]}
 */
public class PageResult<T> {

    private long total;
    private int page;
    private int size;
    private List<T> items;

    private PageResult() {
    }

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        PageResult<T> result = new PageResult<T>();
        result.items = items;
        result.total = total;
        result.page = page;
        result.size = size;
        return result;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }
}
