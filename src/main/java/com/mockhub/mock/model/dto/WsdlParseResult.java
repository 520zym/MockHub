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

        public WsdlOperation() {
        }

        public WsdlOperation(String operationName, String soapAction) {
            this.operationName = operationName;
            this.soapAction = soapAction;
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
    }
}
