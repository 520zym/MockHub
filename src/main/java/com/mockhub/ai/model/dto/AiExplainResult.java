package com.mockhub.ai.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 匹配诊断结果：结构化判定链路 + 可读结论 + 修正建议。
 */
public class AiExplainResult {

    private String conclusion;
    private String summary;
    private String matchedResponseId;
    private String matchedResponseName;
    private String suggestion;
    private List<AiChainItem> chain = new ArrayList<AiChainItem>();
    private String provider;
    private String model;

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getMatchedResponseId() {
        return matchedResponseId;
    }

    public void setMatchedResponseId(String matchedResponseId) {
        this.matchedResponseId = matchedResponseId;
    }

    public String getMatchedResponseName() {
        return matchedResponseName;
    }

    public void setMatchedResponseName(String matchedResponseName) {
        this.matchedResponseName = matchedResponseName;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public List<AiChainItem> getChain() {
        return chain;
    }

    public void setChain(List<AiChainItem> chain) {
        this.chain = chain;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
