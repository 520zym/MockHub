package com.mockhub.ai.service;

import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiSettingsUpdateRequest;
import com.mockhub.ai.repository.AiSettingsRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 供应商保存时的默认值回填与 Ollama 免 Key 判断。 */
class AiSettingsServiceTest {

    private AiSettingsRepository repository;
    private AiSettingsService service;
    private AiSettings settings;

    @BeforeEach
    void setUp() {
        repository = mock(AiSettingsRepository.class);
        settings = new AiSettings();
        settings.setEnabled(true);
        settings.setVendor(AiProviderPresets.CUSTOM);
        when(repository.load()).thenReturn(settings);
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", "test-user");
        details.put("username", "tester");
        details.put("globalRole", "SUPER_ADMIN");
        details.put("teamIds", Collections.emptyList());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("tester", null);
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
        service = new AiSettingsService(repository, mock(OpenAiCompatibleProvider.class),
                mock(PermissionChecker.class), mock(AiKeyCipher.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fillsDefaultsWhenOnlyVendorProvided() {
        AiSettingsUpdateRequest request = request("kimi");

        service.update(request);

        assertEquals("kimi", settings.getVendor());
        assertEquals("https://api.moonshot.cn/v1", settings.getBaseUrl());
        assertEquals("kimi-k3", settings.getModel());
    }

    @Test
    void rejectsUnknownVendor() {
        assertThrows(BizException.class, () -> service.update(request("unknown")));
    }

    @Test
    void ollamaDoesNotRequireApiKey() {
        settings.setVendor("ollama");
        settings.setBaseUrl("http://localhost:11434/v1");
        settings.setModel("llama3.1");
        settings.setApiKey(null);
        assertTrue(service.isConfigured(settings));
    }

    private AiSettingsUpdateRequest request(String vendor) {
        AiSettingsUpdateRequest request = new AiSettingsUpdateRequest();
        request.setEnabled(true);
        request.setVendor(vendor);
        request.setProvider(AiSettings.PROVIDER_OPENAI);
        request.setCapResponseBody(true);
        request.setCapConditions(true);
        request.setCapExplain(true);
        request.setCapDrafts(true);
        request.setAccessScope(AiSettings.SCOPE_ALL);
        return request;
    }
}