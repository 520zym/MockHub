package com.mockhub.ai.service;

import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.repository.AiSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 启动迁移：把历史明文 API Key 加密回写。幂等，解密失败不影响启动。
 */
@Component
public class AiKeyMigration {

    private static final Logger log = LoggerFactory.getLogger(AiKeyMigration.class);

    private final AiSettingsRepository repository;
    private final AiKeyCipher cipher;

    public AiKeyMigration(AiSettingsRepository repository, AiKeyCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @PostConstruct
    public void migrate() {
        try {
            AiSettings settings = repository.load();
            if (settings == null || settings.getApiKey() == null || settings.getApiKey().trim().isEmpty()) {
                return;
            }
            if (cipher.isEncrypted(settings.getApiKey())) {
                return;
            }
            settings.setApiKey(cipher.encrypt(settings.getApiKey()));
            repository.save(settings);
            log.info("AI API Key 已完成静态加密迁移");
        } catch (Exception e) {
            log.warn("AI API Key 加密迁移失败，保留原值：{}", e.getMessage());
        }
    }
}
