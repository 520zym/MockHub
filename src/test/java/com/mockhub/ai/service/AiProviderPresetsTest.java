package com.mockhub.ai.service;

import com.mockhub.ai.model.AiProviderPreset;
import com.mockhub.common.model.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 供应商预设与默认值。 */
class AiProviderPresetsTest {

    @Test
    void exposesAllPresets() {
        assertEquals(8, AiProviderPresets.all().size());
        AiProviderPreset deepseek = AiProviderPresets.get("deepseek");
        assertNotNull(deepseek);
        assertEquals("https://api.deepseek.com/v1", deepseek.getBaseUrl());
        assertEquals("deepseek-chat", deepseek.getDefaultModel());
        assertTrue(deepseek.getModels().contains("deepseek-reasoner"));
        assertTrue(AiProviderPresets.get("ollama").isLocal());
    }

    @Test
    void normalizesAndRejectsUnknownVendor() {
        assertEquals("kimi", AiProviderPresets.normalize("KIMI"));
        assertEquals(AiProviderPresets.CUSTOM, AiProviderPresets.normalize(null));
        assertThrows(BizException.class, () -> AiProviderPresets.normalize("unknown"));
    }

    @Test
    void infersVendorFromBaseUrl() {
        assertEquals("deepseek", AiProviderPresets.infer("https://api.deepseek.com/v1"));
        assertEquals("kimi", AiProviderPresets.infer("https://api.moonshot.cn/v1"));
        assertEquals("glm", AiProviderPresets.infer("https://open.bigmodel.cn/api/paas/v4"));
        assertEquals("ollama", AiProviderPresets.infer("http://localhost:11434/v1"));
        assertEquals(AiProviderPresets.CUSTOM, AiProviderPresets.infer("https://example.com/v1"));
    }
}
