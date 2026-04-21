package com.mockhub.mock.model.dto;

import java.util.List;

/**
 * WSDL 文件解析结果
 */
public class WsdlParseResult {

    /** WSDL 文件名 */
    private String fileName;

    /** 解析出的 SOAP 操作列表 */
    private List<WsdlOperation> operations;

    public WsdlParseResult() {
    }

    public WsdlParseResult(String fileName, List<WsdlOperation> operations) {
        this.fileName = fileName;
        this.operations = operations;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<WsdlOperation> getOperations() {
        return operations;
    }

    public void setOperations(List<WsdlOperation> operations) {
        this.operations = operations;
    }

    /**
     * WSDL 中解析出的单个操作信息
     */
    public static class WsdlOperation {

        /** 操作名称 */
        private String operationName;

        /** SOAP Action 标识 */
        private String soapAction;

        /**
         * 按 WSDL 里的 XSD 递归生成的响应体骨架（SOAP Envelope XML 字符串）。
         * <p>
         * 前端合并策略：仅当对应 operation 的 responseBody 为空时才使用此值，
         * 已有的用户响应体不会被覆盖。
         * <p>
         * 可为 null（例如 XSD 解析失败）。
         */
        private String suggestedResponseBody;

        public WsdlOperation() {
        }

        public WsdlOperation(String operationName, String soapAction) {
            this.operationName = operationName;
            this.soapAction = soapAction;
        }

        public WsdlOperation(String operationName, String soapAction, String suggestedResponseBody) {
            this.operationName = operationName;
            this.soapAction = soapAction;
            this.suggestedResponseBody = suggestedResponseBody;
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

        public String getSuggestedResponseBody() {
            return suggestedResponseBody;
        }

        public void setSuggestedResponseBody(String suggestedResponseBody) {
            this.suggestedResponseBody = suggestedResponseBody;
        }
    }
}
