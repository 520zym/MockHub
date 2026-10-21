package com.mockhub.ai.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 生成条件规则结果。
 */
public class AiConditionsResult {

    private List<AiConditionSuggestion> rules = new ArrayList<AiConditionSuggestion>();
    private List<String> warnings = new ArrayList<String>();
    private String provider;
    private String model;

    public List<AiConditionSuggestion> getRules() {
        return rules;
    }

    public void setRules(List<AiConditionSuggestion> rules) {
        this.rules = rules;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
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
