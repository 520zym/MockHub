package com.mockhub.ai.service;

import com.mockhub.common.config.DataProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API Key 静态加密：往返、旧明文兼容、密钥错误。
 */
class AiKeyCipherTest {

    private AiKeyCipher cipher(Path dir) {
        DataProperties properties = new DataProperties();
        properties.setPath(dir.toString());
        return new AiKeyCipher(properties);
    }

    @Test
    void encryptsAndDecrypts() throws Exception {
        Path dir = Files.createTempDirectory("mockhub-ai-key");
        AiKeyCipher cipher = cipher(dir);
        String encrypted = cipher.encrypt("sk-test-123456");
        assertTrue(encrypted.startsWith(AiKeyCipher.PREFIX));
        assertNotEquals("sk-test-123456", encrypted);
        assertEquals("sk-test-123456", cipher.decrypt(encrypted));
        assertTrue(Files.exists(dir.resolve(".ai-key")));
    }

    @Test
    void returnsLegacyPlaintextUnchanged() throws Exception {
        Path dir = Files.createTempDirectory("mockhub-ai-key-legacy");
        assertEquals("raw-key", cipher(dir).decrypt("raw-key"));
    }

    @Test
    void failsWithDifferentKeyFile() throws Exception {
        Path dirA = Files.createTempDirectory("mockhub-ai-key-a");
        Path dirB = Files.createTempDirectory("mockhub-ai-key-b");
        String encrypted = cipher(dirA).encrypt("secret");
        assertThrows(IllegalStateException.class, () -> cipher(dirB).decrypt(encrypted));
    }
}
