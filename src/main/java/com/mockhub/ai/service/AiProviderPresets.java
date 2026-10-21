package com.mockhub.ai.service;

import com.mockhub.ai.model.AiProviderPreset;
import com.mockhub.common.model.BizException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 内置 AI 供应商目录。
 * <p>
 * 推荐模型随版本维护；真实候选列表由 /api/ai/models 在运行时向服务商查询。
 */
public final class AiProviderPresets {

    public static final String CUSTOM = "custom";

    private static final Map<String, AiProviderPreset> PRESETS;

    static {
        Map<String, AiProviderPreset> map = new LinkedHashMap<String, AiProviderPreset>();
        add(map, new AiProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
                "deepseek-chat", Arrays.asList("deepseek-chat", "deepseek-reasoner"), false));
        add(map, new AiProviderPreset("kimi", "Kimi", "https://api.moonshot.cn/v1",
                "kimi-k3", Arrays.asList("kimi-k3", "kimi-k2-0905-preview", "moonshot-v1-8k"), false));
        add(map, new AiProviderPreset("minimax", "MiniMax", "https://api.minimax.cn/v1",
                "MiniMax-M3", Arrays.asList("MiniMax-M3", "MiniMax-M3.1-Flash", "abab6.5s-chat"), false));
        add(map, new AiProviderPreset("glm", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
                "glm-4.6", Arrays.asList("glm-4.6", "glm-4.5", "glm-4-flash"), false));
        add(map, new AiProviderPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen-plus", Arrays.asList("qwen-plus", "qwen-max", "qwen-turbo"), false));
        add(map, new AiProviderPreset("openai", "OpenAI", "https://api.openai.com/v1",
                "gpt-4o-mini", Arrays.asList("gpt-4o-mini", "gpt-4o", "o3-mini"), false));
        add(map, new AiProviderPreset("ollama", "Ollama", "http://localhost:11434/v1",
                "llama3.1", Arrays.asList("llama3.1", "qwen2.5", "deepseek-r1"), true));
        add(map, new AiProviderPreset(CUSTOM, "自定义", "",
                "", Collections.<String>emptyList(), false));
        PRESETS = Collections.unmodifiableMap(map);
    }

    private AiProviderPresets() {
    }

    private static void add(Map<String, AiProviderPreset> map, AiProviderPreset preset) {
        map.put(preset.getKey(), preset);
    }

    public static List<AiProviderPreset> all() {
        return new ArrayList<AiProviderPreset>(PRESETS.values());
    }

    public static AiProviderPreset get(String key) {
        if (key == null) {
            return null;
        }
        return PRESETS.get(key.trim().toLowerCase(Locale.ROOT));
    }

    /** 校验并规范化 vendor；空值按 custom 处理。 */
    public static String normalize(String vendor) {
        if (vendor == null || vendor.trim().isEmpty()) {
            return CUSTOM;
        }
        String key = vendor.trim().toLowerCase(Locale.ROOT);
        if (!PRESETS.containsKey(key)) {
            throw new BizException(40001, "不支持的 AI 供应商");
        }
        return key;
    }

    /** 根据已有 Base URL 推断供应商，供旧库迁移与无 vendor 配置兜底。 */
    public static String infer(String baseUrl) {
        if (baseUrl == null) {
            return CUSTOM;
        }
        String url = baseUrl.toLowerCase(Locale.ROOT);
        if (url.contains("api.deepseek.com")) return "deepseek";
        if (url.contains("moonshot")) return "kimi";
        if (url.contains("minimax")) return "minimax";
        if (url.contains("bigmodel")) return "glm";
        if (url.contains("dashscope")) return "qwen";
        if (url.contains("api.openai.com")) return "openai";
        if (url.contains("11434")) return "ollama";
        return CUSTOM;
    }
}
