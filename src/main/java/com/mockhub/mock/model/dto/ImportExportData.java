package com.mockhub.mock.model.dto;

import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.model.entity.GlobalHeader;
import com.mockhub.mock.model.entity.Tag;

import java.util.List;

/**
 * 接口导入/导出的 JSON 数据结构
 */
public class ImportExportData {

    /** 格式版本号 */
    private String version;

    /** 导出时间（ISO 格式） */
    private String exportedAt;

    /** 团队名称 */
    private String teamName;

    /** 分组列表 */
    private List<ApiGroup> groups;

    /** 标签列表 */
    private List<Tag> tags;

    /** 接口列表 */
    private List<ApiDefinition> apis;

    /** 全局响应头列表 */
    private List<GlobalHeader> globalHeaders;

    public ImportExportData() {
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(String exportedAt) {
        this.exportedAt = exportedAt;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public List<ApiGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<ApiGroup> groups) {
        this.groups = groups;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public List<ApiDefinition> getApis() {
        return apis;
    }

    public void setApis(List<ApiDefinition> apis) {
        this.apis = apis;
    }

    public List<GlobalHeader> getGlobalHeaders() {
        return globalHeaders;
    }

    public void setGlobalHeaders(List<GlobalHeader> globalHeaders) {
        this.globalHeaders = globalHeaders;
    }
}
