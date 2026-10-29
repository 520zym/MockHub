package com.mockhub.mock.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.filter.JwtAuthFilter;
import com.mockhub.common.model.CurrentUser;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.common.util.CurrentUserProvider;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.service.ApiMatchPreviewService;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiMatchPreviewControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;
    private PermissionChecker permissions;
    private ApiRepository apis;
    private ApiResponseRepository responses;
    private CurrentUserProvider users;
    private static final String ENDPOINT = "/api/apis/preview-match";
    @BeforeEach void setup() {
        permissions = mock(PermissionChecker.class); TeamRepository teams = mock(TeamRepository.class);
        when(teams.findById("team")).thenReturn(new Team());
        apis = mock(ApiRepository.class); responses = mock(ApiResponseRepository.class);
        ApiMatchPreviewService service = new ApiMatchPreviewService(permissions, teams, apis, responses, new ResponseMatcher(responses, mapper), mapper);
        JwtUtil jwt = mock(JwtUtil.class); users = mock(CurrentUserProvider.class);
        Claims claims = new DefaultClaims(); claims.setSubject("u"); claims.put("auth_version", 0); claims.put("sid", "session");
        when(jwt.validateToken("valid-token")).thenReturn(claims);
        when(users.findBySession("u", 0, "session")).thenReturn(new CurrentUser("u", "user", "USER", Collections.singletonList("team")));
        mvc = MockMvcBuilders.standaloneSetup(new ApiMatchPreviewController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new JwtAuthFilter(jwt, mapper, users)).build();
    }
    @Test void postUrlReturnsSmallResultMetadataAndNoResponsePayload() throws Exception {
        String json = "{\"teamId\":\"team\",\"responseMode\":\"CONDITION\",\"responses\":[{\"clientKey\":\"temp-1\",\"isActive\":true,\"responseCode\":201,\"bodyType\":\"FILE\",\"responseBody\":\"private payload\",\"filePath\":\"../../no-file-read\"}],\"sample\":{\"body\":\"{}\",\"query\":{},\"headers\":{}}}";
        String response = mvc.perform(post(ENDPOINT).header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchedIndex").value(0)).andExpect(jsonPath("$.data.matchedClientKey").value("temp-1"))
                .andExpect(jsonPath("$.data.responseCode").value(201)).andExpect(jsonPath("$.data.usingSavedConfiguration").value(false))
                .andExpect(jsonPath("$.data.reason").value("SINGLE_ENABLED")).andReturn().getResponse().getContentAsString();
        assertTrue(response.length() < 1024); assertFalse(response.contains("private payload")); assertFalse(response.contains("filePath"));
        assertFalse(response.contains("responseBody")); verifyNoInteractions(apis, responses);
        verify(users).findBySession("u", 0, "session"); verifyNoMoreInteractions(users);
    }
    @Test void jwtRequiredAndInvalidTokenNeverEntersService() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ENDPOINT).header("Authorization", "Bearer invalid").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(permissions, apis, responses, users);
    }
    @Test void invalidConditionsAndMissingTeamAreBusiness40001() throws Exception {
        for (String json : new String[]{"{}", "{\"teamId\":\"team\",\"responseMode\":\"CONDITION\",\"responses\":[{\"isActive\":true,\"conditions\":\"not-json\"}]}"}) {
            mvc.perform(post(ENDPOINT).header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40001));
        }
    }
    @Test void malformedEnvelopeJsonIsSanitized40001() throws Exception {
        String output = mvc.perform(post(ENDPOINT).header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content("{sensitive-invalid-body"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40001)).andReturn().getResponse().getContentAsString();
        assertFalse(output.contains("sensitive")); verifyNoInteractions(permissions);
    }
    @Test void randomContractHasCandidatesNotFixedMatchedIndex() throws Exception {
        String json = "{\"teamId\":\"team\",\"responseMode\":\"RANDOM\",\"responses\":[{\"id\":\"a\",\"isActive\":true},{\"id\":\"off\",\"isActive\":false},{\"clientKey\":\"new\",\"isActive\":true}]}";
        JsonNode data = mapper.readTree(mvc.perform(post(ENDPOINT).header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsByteArray()).path("data");
        assertEquals("RANDOM_EQUAL_PROBABILITY", data.path("reason").asText()); assertEquals(2, data.path("enabledCount").asInt());
        assertEquals(2, data.path("candidates").size()); assertFalse(data.has("matchedIndex"));
    }
}
