package com.mockhub.mock.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.service.ApiService;
import com.mockhub.mock.service.MockFileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiControllerSaveResponseTest {

    @Test
    void createShouldReturnSmallJsonDespiteLargeResponseBody() throws Exception {
        verifySave(false);
    }

    @Test
    void updateShouldReturnSmallJsonDespiteLargeResponseBody() throws Exception {
        verifySave(true);
    }

    private void verifySave(boolean update) throws Exception {
        ApiService service = mock(ApiService.class);
        ApiDefinition api = new ApiDefinition();
        api.setId("api-1");
        api.setName("大返回体接口");
        api.setPath("/large");
        api.setMethod("POST");
        api.setTeamId("team-1");
        api.setType("REST");
        char[] body = new char[5 * 1024 * 1024];
        java.util.Arrays.fill(body, 'x');
        api.setResponseBody(new String(body));
        when(service.create(any(ApiDefinitionDTO.class))).thenReturn(api);
        when(service.update(eq("api-1"), any(ApiDefinitionDTO.class))).thenReturn(api);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApiController(service,
                mock(MockFileStorageService.class), mock(PermissionChecker.class))).build();

        MockHttpServletRequestBuilder request = update ? put("/api/apis/api-1") : post("/api/apis");
        MockHttpServletResponse response = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"大返回体接口\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray();
        assertTrue(bytes.length < 1024, "保存响应应小于 1 KB，不应回传 5 MB 响应体");
        JsonNode result = new ObjectMapper().readTree(bytes);
        assertEquals(0, result.path("code").asInt());
        JsonNode data = result.path("data");
        assertEquals(6, data.size());
        assertEquals("api-1", data.path("id").asText());
        assertEquals("大返回体接口", data.path("name").asText());
        assertEquals("/large", data.path("path").asText());
        assertEquals("POST", data.path("method").asText());
        assertEquals("team-1", data.path("teamId").asText());
        assertEquals("REST", data.path("type").asText());
        assertFalse(data.has("responseBody"));
        assertEquals(body.length, api.getResponseBody().length(), "返回精简不能修改业务实体");
        if (update) {
            verify(service).update(eq("api-1"), any(ApiDefinitionDTO.class));
        } else {
            verify(service).create(any(ApiDefinitionDTO.class));
        }
    }
}
