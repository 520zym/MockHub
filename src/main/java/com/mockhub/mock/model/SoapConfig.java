package com.mockhub.mock.model;

import java.util.List;

/**
 * SOAP 接口配置
 * <p>
 * 以 JSON 字符串存储在 api_definition.soap_config 字段中。
 * 包含 WSDL 文件名和各 operation 的独立响应配置。
 */
public class SoapConfig {

    /** 关联的 WSDL 文件名 */
    private String wsdlFileName;

    /** SOAP 操作列表，每个操作独立配置响应 */
    private List<SoapOperation> operations;

    public SoapConfig() {
    }

    public String getWsdlFileName() {
        return wsdlFileName;
    }

    public void setWsdlFileName(String wsdlFileName) {
        this.wsdlFileName = wsdlFileName;
    }

    public List<SoapOperation> getOperations() {
        return operations;
    }

    public void setOperations(List<SoapOperation> operations) {
        this.operations = operations;
    }

    @Override
    public String toString() {
        return "SoapConfig{" +
                "wsdlFileName='" + wsdlFileName + '\'' +
                ", operations=" + (operations != null ? operations.size() : 0) +
                '}';
    }
}
