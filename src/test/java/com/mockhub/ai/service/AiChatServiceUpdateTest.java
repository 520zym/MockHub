package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiChatRequest;
import com.mockhub.ai.model.dto.AiChatResponse;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.mock.model.dto.ApiDefinitionDetailVO;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.service.ApiService;
import com.mockhub.stats.service.StatsService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 对话助手识别并生成「修改已有接口」草稿。 */
class AiChatServiceUpdateTest {

    @Test
    void mockProviderCreatesUpdateCardForContextApi() {
        AiSettingsService settingsService = mock(AiSettingsService.class);
        AiService aiService = mock(AiService.class);
        ApiService apiService = mock(ApiService.class);
        PendingActionService pendingActionService = mock(PendingActionService.class);

        AiSettings settings = new AiSettings();
        settings.setProvider(AiSettings.PROVIDER_MOCK);
        settings.setEnabled(true);
        settings.setCapDrafts(true);
        when(settingsService.assertUsable(any(), any())).thenReturn(settings);

        ApiDefinitionDetailVO detail = new ApiDefinitionDetailVO();
        detail.setId("api-1");
        detail.setTeamId("team-1");
        detail.setType("REST");
        detail.setName("用户列表");
        detail.setMethod("GET");
        detail.setPath("/api/users");
        ApiResponse response = new ApiResponse();
        response.setId("resp-1");
        response.setName("成功响应");
        response.setBodyType("TEXT");
        response.setContentType("application/json");
        response.setActive(true);
        response.setResponseBody("{\"data\":[{\"id\":1}]}");
        List<ApiResponse> responses = new ArrayList<ApiResponse>();
        responses.add(response);
        detail.setResponses(responses);
        when(apiService.getById("api-1")).thenReturn(detail);

        AiResponseResult generated = new AiResponseResult();
        generated.setContent("{\"data\":[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4}]}");
        when(aiService.generateResponse(any())).thenReturn(generated);

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("type", "PENDING_ACTION");
        card.put("actionType", "UPDATE_API");
        card.put("actionId", "action-1");
        when(pendingActionService.createApiUpdateDraft(any(), anyString())).thenReturn(card);

        AiChatService service = new AiChatService(settingsService, mock(OpenAiCompatibleProvider.class),
                mock(MockAiGenerator.class), aiService, apiService, mock(StatsService.class),
                pendingActionService, new ObjectMapper());

        AiChatRequest request = new AiChatRequest();
        request.setTeamId("team-1");
        request.setContextApiId("api-1");
        request.setMessage("把返回值数组弄大一些");

        AiChatResponse reply = service.chat(request);

        assertEquals(1, reply.getCards().size());
        assertEquals("UPDATE_API", reply.getCards().get(0).get("actionType"));
        assertEquals("PENDING_ACTION", reply.getCards().get(0).get("type"));
        verify(pendingActionService).createApiUpdateDraft(any(), anyString());
        assertTrue(reply.getReply().contains("修改草稿"));
    }
}