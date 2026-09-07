package com.mockhub.mock.model.dto;

import com.mockhub.mock.model.entity.ApiDefinition;

/**
 * 接口保存结果轻量 VO。
 * <p>
 * 保存接口只需要返回基础识别字段，避免大响应体在弱网下被重复回传。
 */
public class ApiSaveResultVO {

    private String id;
    private String name;
    private String path;
    private String method;
    private String teamId;
    private String type;

    public static ApiSaveResultVO from(ApiDefinition api) {
        ApiSaveResultVO vo = new ApiSaveResultVO();
        vo.setId(api.getId());
        vo.setName(api.getName());
        vo.setPath(api.getPath());
        vo.setMethod(api.getMethod());
        vo.setTeamId(api.getTeamId());
        vo.setType(api.getType());
        return vo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
