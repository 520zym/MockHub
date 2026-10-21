package com.mockhub.ai.model.dto;

/**
 * AI 修改已有接口的待确认草稿。
 * <p>
 * 只修改指定返回体的正文；路径、方法、条件、文件元数据保持不变。
 */
public class AiApiUpdateDraft {

    private String apiId;
    private String responseId;
    private String teamId;
    private String apiName;
    private String method;
    private String path;
    private String responseName;
    private String oldBody;
    private String newBody;

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
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

    public String getResponseName() {
        return responseName;
    }

    public void setResponseName(String responseName) {
        this.responseName = responseName;
    }

    public String getOldBody() {
        return oldBody;
    }

    public void setOldBody(String oldBody) {
        this.oldBody = oldBody;
    }

    public String getNewBody() {
        return newBody;
    }

    public void setNewBody(String newBody) {
        this.newBody = newBody;
    }
}
