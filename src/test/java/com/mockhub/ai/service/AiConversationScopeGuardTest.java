package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.repository.AiConversationRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiConversationScopeGuardTest {
    private AiConversationRepository repository;
    private PermissionChecker permission;
    private TeamRepository teams;
    private AiConversationService service;
    @BeforeEach void setup() {
        Map<String,Object> details = new HashMap<String,Object>(); details.put("userId", "u"); details.put("globalRole", "SUPER_ADMIN");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", null, Collections.emptyList()); auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
        repository = mock(AiConversationRepository.class); permission = mock(PermissionChecker.class); teams = mock(TeamRepository.class);
        service = new AiConversationService(repository, new ObjectMapper(), permission, teams);
        AiConversation conversation = new AiConversation(); conversation.setId("c"); conversation.setUserId("u"); conversation.setTeamId("a");
        when(repository.find("c", "u")).thenReturn(conversation); when(repository.listByUser("u",30)).thenReturn(Collections.singletonList(conversation)); when(teams.findById(anyString())).thenReturn(new Team());
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void membershipDatabaseFailuresAreNeverDowngradedToAnEmptyListOrOwnerOnlyRead() {
        doThrow(new DataAccessResourceFailureException("database unavailable")).when(permission).checkTeamAccess("a");
        assertThrows(DataAccessResourceFailureException.class, () -> service.list("u"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.detail("c","u"));
        verify(repository, never()).listMessages(anyString());
    }
    @Test void unexpectedPermissionErrorsAreNotSwallowedByListFiltering() {
        doThrow(new BizException(50001,"unexpected")).when(permission).checkTeamAccess("a");
        assertEquals(50001,assertThrows(BizException.class, () -> service.list("u")).getCode());
    }
    @Test void missingTeamsAreRejectedEvenForSuperAdminAndBeforeMessageContentLoads() {
        when(teams.findById("a")).thenReturn(null);
        assertTrue(service.list("u").isEmpty());
        assertEquals(40403,assertThrows(BizException.class, () -> service.detail("c","u")).getCode());
        verify(repository, never()).listMessages(anyString());
    }
    @Test void appendRejectsCrossTeamBeforeAnyConversationOrMessageMutation() {
        assertEquals(40102,assertThrows(BizException.class, () -> service.appendExchange("c","u","b","question","reply",Collections.emptyList(),"mock","mock")).getCode());
        verify(repository, never()).insert(any()); verify(repository, never()).insertMessage(any()); verify(repository, never()).updateMeta(any(),any(),any());
    }
    @Test void globalAdminCannotSupplyAnotherOwnerIdDirectlyToTheService() {
        assertEquals(40305,assertThrows(BizException.class, () -> service.list("other")).getCode());
        assertEquals(40305,assertThrows(BizException.class, () -> service.detail("c","other")).getCode());
        verify(repository,never()).listByUser("other",30); verify(repository,never()).find("c","other");
    }
}
