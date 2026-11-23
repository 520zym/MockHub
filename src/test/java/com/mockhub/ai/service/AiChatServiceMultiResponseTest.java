package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiChatRequest;
import com.mockhub.ai.model.dto.AiChatResponse;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.service.ApiService;
import com.mockhub.stats.service.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceMultiResponseTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mockProviderCreatesThreeConditionalAndOneDefaultResponse() {
        AiSettingsService settingsService = mock(AiSettingsService.class);
        PendingActionService pendingActionService = mock(PendingActionService.class);
        AiConversationService conversationService = mock(AiConversationService.class);

        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", "user-1");
        details.put("username", "tester");
        details.put("globalRole", "SUPER_ADMIN");
        details.put("teamIds", Collections.emptyList());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("tester", null);
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);

        AiSettings settings = new AiSettings();
        settings.setProvider(AiSettings.PROVIDER_MOCK);
        settings.setEnabled(true);
        settings.setCapDrafts(true);
        when(settingsService.assertUsable(any(), any())).thenReturn(settings);

        AiConversation conversation = new AiConversation();
        conversation.setId("conv-1");
        conversation.setTitle("测试会话");
        when(conversationService.appendExchange(any(), anyString(), any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(conversation);
        when(pendingActionService.createApiDraft(any(), anyString()))
                .thenReturn(Collections.<String, Object>singletonMap("actionId", "action-1"));

        MockAiGenerator generator = mock(MockAiGenerator.class);
        AiResponseResult generated = new AiResponseResult();
        generated.setContent("{\"code\":0}");
        when(generator.response(any())).thenReturn(generated);
        AiChatService service = new AiChatService(settingsService, mock(OpenAiCompatibleProvider.class),
                generator, mock(AiService.class), mock(ApiService.class),
                mock(StatsService.class), pendingActionService, conversationService, new ObjectMapper());

        AiChatRequest request = new AiChatRequest();
        request.setTeamId("team-1");
        request.setMessage("随便帮我创建一个接口，返回体设置3个，每个命中条件不同，再设置一个默认返回体");

        AiChatResponse response = service.chat(request);

        assertEquals(1, response.getCards().size());
        ArgumentCaptor<ApiDefinitionDTO> captor = ArgumentCaptor.forClass(ApiDefinitionDTO.class);
        verify(pendingActionService).createApiDraft(captor.capture(), anyString());
        List<ApiResponseDTO> responses = captor.getValue().getResponses();
        assertEquals(4, responses.size());
        long conditional = responses.stream().filter(item -> item.getConditions() != null && !item.getConditions().trim().isEmpty()).count();
        long defaults = responses.stream().filter(item -> item.getConditions() == null || item.getConditions().trim().isEmpty()).count();
        assertEquals(3, conditional);
        assertEquals(1, defaults);
    }
}
