package com.mockhub.ai.model.dto;

/**
 * 判定链路中的一条返回体：命中 / 未命中 / 未参与。
 */
public class AiChainItem {

    private int index;
    private String responseId;
    private String name;
    private String status;
    private String detail;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
