package com.mockhub.mock.model;

/**
 * SOAP 操作配置
 * <p>
 * 每个 operation 独立配置响应状态码、延迟和响应体。
 */
public class SoapOperation {

    /** 操作名称 */
    private String operationName;

    /** SOAP Action 标识 */
    private String soapAction;

    /** 响应状态码 */
    private int responseCode;

    /** 延迟毫秒数 */
    private int delayMs;

    /** 响应体（SOAP XML） */
    private String responseBody;

    public SoapOperation() {
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public void setSoapAction(String soapAction) {
        this.soapAction = soapAction;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        this.delayMs = delayMs;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    @Override
    public String toString() {
        return "SoapOperation{" +
                "operationName='" + operationName + '\'' +
                ", soapAction='" + soapAction + '\'' +
                ", responseCode=" + responseCode +
                ", delayMs=" + delayMs +
                '}';
    }
}
