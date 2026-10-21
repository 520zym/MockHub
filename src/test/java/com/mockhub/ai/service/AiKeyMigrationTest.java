package com.mockhub.ai.service;

import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.repository.AiSettingsRepository;
import com.mockhub.common.config.DataProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 老库明文 Key 的启动迁移。
 */
class AiKeyMigrationTest {

    @Test
    void encryptsLegacyPlaintextKey() throws Exception {
        Path dir = Files.createTempDirectory("mockhub-ai-migrate");
        DataProperties properties = new DataProperties();
        properties.setPath(dir.toString());
        AiKeyCipher cipher = new AiKeyCipher(properties);

        AiSettingsRepository repository = mock(AiSettingsRepository.class);
        AiSettings settings = new AiSettings();
        settings.setApiKey("sk-legacy-plain");
        when(repository.load()).thenReturn(settings);

        new AiKeyMigration(repository, cipher).migrate();

        ArgumentCaptor<AiSettings> captor = ArgumentCaptor.forClass(AiSettings.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getApiKey().startsWith(AiKeyCipher.PREFIX));
        assertTrue(!captor.getValue().getApiKey().contains("sk-legacy-plain"));
    }
}
