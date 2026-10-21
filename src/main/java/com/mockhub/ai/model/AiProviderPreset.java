package com.mockhub.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 供应商预设：默认接口地址、默认模型与推荐模型列表。
 * <p>
 * 所有预设都走 OpenAI 兼容协议；custom 由用户手填。
 */
public class AiProviderPreset {

    private final String key;
    private final String name;
    private final String baseUrl;
    private final String defaultModel;
    private final List<String> models;
    private final boolean local;

    public AiProviderPreset(String key, String name, String baseUrl, String defaultModel,
                            List<String> models, boolean local) {
        this.key = key;
        this.name = name;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.models = Collections.unmodifiableList(new ArrayList<String>(models));
        this.local = local;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public List<String> getModels() {
        return models;
    }

    public boolean isLocal() {
        return local;
    }
}
