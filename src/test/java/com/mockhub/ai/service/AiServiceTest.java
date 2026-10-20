package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiConditionSuggestion;
import com.mockhub.ai.model.dto.AiConditionsResult;
import com.mockhub.ai.model.dto.AiGenerateRequest;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.common.model.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 输出清洗与校验：模型可能返回代码围栏、非法 JSON 或示例中不存在的字段。
 */
class AiServiceTest {

    private static final String FENCE = new String(new char[]{(char) 96, (char) 96, (char) 96});

    private AiSettingsService settingsService;
    private OpenAiCompatibleProvider provider;
    private MockAiGenerator mockGenerator;
    private AiService service;
    private AiSettings settings;

    @BeforeEach
    void setUp() {
        settingsService = mock(AiSettingsService.class);
        provider = mock(OpenAiCompatibleProvider.class);
        mockGenerator = mock(MockAiGenerator.class);
        settings = new AiSettings();
        settings.setProvider(AiSettings.PROVIDER_OPENAI);
        settings.setModel("test-model");
        when(settingsService.assertUsable(anyString(), anyString())).thenReturn(settings);
        service = new AiService(settingsService, provider, mockGenerator, new ObjectMapper());
    }

    private AiGenerateRequest request() {
        AiGenerateRequest req = new AiGenerateRequest();
        req.setTeamId("team-1");
        req.setApiName("用户信息");
        req.setApiType("REST");
        return req;
    }

    @Test
    void stripsCodeFenceAndValidatesJson() {
        when(provider.complete(any(AiSettings.class), anyString(), anyString()))
                .thenReturn(FENCE + "json\n{\"code\":0,\"data\":{\"name\":\"张三\"}}\n" + FENCE);

        AiResponseResult result = service.generateResponse(request());

        assertTrue(result.getContent().startsWith("{"));
        assertTrue(result.getContent().endsWith("}"));
    }

    @Test
    void rejectsInvalidJson() {
        when(provider.complete(any(AiSettings.class), anyString(), anyString()))
                .thenReturn("抱歉，我无法生成");

        BizException e = assertThrows(BizException.class, () -> service.generateResponse(request()));
        assertEquals(50211, e.getCode());
    }

    @Test
    void rejectsInvalidXml() {
        AiGenerateRequest req = request();
        req.setApiType("SOAP");
        when(provider.complete(any(AiSettings.class), anyString(), anyString()))
                .thenReturn("<response><unclosed></response>");

        BizException e = assertThrows(BizException.class, () -> service.generateResponse(req));
        assertEquals(50212, e.getCode());
    }

    @Test
    void marksUnknownSampleFieldAsInvalid() {
        AiGenerateRequest req = request();
        req.setBodySample("{\"userType\":\"vip\"}");
        when(provider.complete(any(AiSettings.class), anyString(), anyString()))
                .thenReturn("{\"conditions\":[{\"source\":\"BODY\",\"path\":\"orderNo\",\"operator\":\"EQ\",\"value\":\"1\",\"valueType\":\"STRING\"}]}");

        AiConditionsResult result = service.generateConditions(req);

        assertEquals(1, result.getRules().size());
        AiConditionSuggestion rule = result.getRules().get(0);
        assertFalse(rule.isValid());
        assertTrue(rule.getWarning().contains("orderNo"));
    }

    @Test
    void rejectsNonJsonConditionResult() {
        when(provider.complete(any(AiSettings.class), anyString(), anyString()))
                .thenReturn("无法生成");

        BizException e = assertThrows(BizException.class, () -> service.generateConditions(request()));
        assertEquals(50213, e.getCode());
    }
}
