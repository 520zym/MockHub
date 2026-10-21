package com.mockhub.ai.service;

import com.mockhub.ai.model.AiSettings;

/**
 * 通用对话补全接口。具体供应商由 AiSettings.provider 决定。
 */
public interface AiProvider {

    /**
     * 调用模型并返回纯文本内容。
     *
     * @param settings 当前配置（含 baseUrl/model/apiKey）
     * @param system   系统提示词
     * @param user     用户提示词
     * @return 模型返回的文本；为空视为调用失败
     */
    String complete(AiSettings settings, String system, String user);
}
