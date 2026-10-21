package com.mockhub.ai.model.dto;

/**
 * AI 生成请求（响应体 / 条件规则共用）。
 * <p>
 * 字段都可为 null，由 Service 按场景取默认值；teamId 用于团队隔离校验。
 */
public class AiGenerateRequest {

    private String teamId;

    /** REST / SOAP */
    private String apiType;
    private String apiName;
    private String method;
    private String path;
    private String soapOperationName;

    /** 当前返回体信息 */
    private String responseName;
    private String contentType;
    private String existingBody;

    /** 用户描述 */
    private String instruction;

    /** NEW / COMPLETE */
    private String mode;

    /** BRIEF / STANDARD / RICH */
    private String size;

    /** 条件生成：请求体示例与现有规则 */
    private String bodySample;
    private String currentConditions;

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getApiType() {
        return apiType;
    }

    public void setApiType(String apiType) {
        this.apiType = apiType;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSoapOperationName() {
        return soapOperationName;
    }

    public void setSoapOperationName(String soapOperationName) {
        this.soapOperationName = soapOperationName;
    }

    public String getResponseName() {
        return responseName;
    }

    public void setResponseName(String responseName) {
        this.responseName = responseName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExistingBody() {
        return existingBody;
    }

    public void setExistingBody(String existingBody) {
        this.existingBody = existingBody;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getBodySample() {
        return bodySample;
    }

    public void setBodySample(String bodySample) {
        this.bodySample = bodySample;
    }

    public String getCurrentConditions() {
        return currentConditions;
    }

    public void setCurrentConditions(String currentConditions) {
        this.currentConditions = currentConditions;
    }
}
