package com.mockhub.ai.model.dto;

/**
 * 匹配诊断请求：定位一次请求日志即可，其余上下文由服务端读取。
 */
public class AiExplainRequest {

    private String requestLogId;

    public String getRequestLogId() {
        return requestLogId;
    }

    public void setRequestLogId(String requestLogId) {
        this.requestLogId = requestLogId;
    }
}
