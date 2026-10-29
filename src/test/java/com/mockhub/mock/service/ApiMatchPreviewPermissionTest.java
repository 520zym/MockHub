package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.mock.model.dto.ApiMatchPreviewRequest;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.PermissionCheckerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real permission checker + real matcher; only persistence boundaries are mocked. */
class ApiMatchPreviewPermissionTest {
    private Team team;
    private UserRepository users;
    private ApiMatchPreviewService service;
    private ApiMatchPreviewRequest request;
    @BeforeEach void setup() {
        users = mock(UserRepository.class); TeamRepository teams = mock(TeamRepository.class);
        team = new Team(); team.setId("team"); when(teams.findById("team")).thenReturn(team);
        PermissionCheckerImpl permissions = new PermissionCheckerImpl();
        ReflectionTestUtils.setField(permissions, "userRepository", users);
        ReflectionTestUtils.setField(permissions, "teamRepository", teams);
        ApiRepository apis = mock(ApiRepository.class); ApiResponseRepository responses = mock(ApiResponseRepository.class);
        ApiDefinition api = new ApiDefinition(); api.setId("api"); api.setTeamId("team"); api.setType("REST"); api.setResponseMode("CONDITION");
        when(apis.findById("api")).thenReturn(api);
        ApiResponse saved = new ApiResponse(); saved.setId("saved"); saved.setActive(true);
        when(responses.findByApiId("api")).thenReturn(Collections.singletonList(saved));
        ObjectMapper mapper = new ObjectMapper(); service = new ApiMatchPreviewService(permissions, teams, apis, responses, new ResponseMatcher(responses, mapper), mapper);
        request = new ApiMatchPreviewRequest(); request.setTeamId("team"); request.setApiId("api"); request.setResponseMode("CONDITION");
        ApiMatchPreviewRequest.Response draft = new ApiMatchPreviewRequest.Response(); draft.setId("draft"); draft.setActive(true);
        request.setResponses(Collections.singletonList(draft)); login("USER");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @ParameterizedTest @ValueSource(strings={"VIEWER", "EDITOR", "TEAM_ADMIN"})
    void activeRolesUseExactlyTheAuthorizedSource(String role) {
        when(users.findUserTeamRole("user", "team")).thenReturn(role);
        boolean readOnly = "VIEWER".equals(role);
        assertEquals(readOnly, service.preview(request, new MockHttpServletRequest()).isUsingSavedConfiguration());
        assertEquals(readOnly ? "saved" : "draft", service.preview(request, new MockHttpServletRequest()).getMatchedResponseId());
    }
    @ParameterizedTest @ValueSource(strings={"VIEWER", "EDITOR", "TEAM_ADMIN", "SUPER_ADMIN"})
    void archivedAlwaysUsesSavedIncludingSuperAdmin(String role) {
        team.setStatus("ARCHIVED");
        if ("SUPER_ADMIN".equals(role)) login(role); else when(users.findUserTeamRole("user", "team")).thenReturn(role);
        assertTrue(service.preview(request, new MockHttpServletRequest()).isUsingSavedConfiguration());
        assertEquals("saved", service.preview(request, new MockHttpServletRequest()).getMatchedResponseId());
        request.setApiId(null);
        assertEquals(40104, assertThrows(BizException.class, () -> service.preview(request, new MockHttpServletRequest())).getCode());
    }
    @Test void unknownRoleDoesNotDowngradeToSaved() {
        when(users.findUserTeamRole("user", "team")).thenReturn("MYSTERY");
        assertEquals(40102, assertThrows(BizException.class, () -> service.preview(request, new MockHttpServletRequest())).getCode());
    }
    private void login(String role) {
        Map<String, Object> details = new HashMap<String, Object>(); details.put("userId", "user"); details.put("globalRole", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", null, Collections.emptyList());
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
