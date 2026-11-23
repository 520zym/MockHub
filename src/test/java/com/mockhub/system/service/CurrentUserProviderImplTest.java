package com.mockhub.system.service;

import com.mockhub.common.model.CurrentUser;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.model.entity.UserTeam;
import com.mockhub.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserProviderImplTest {

    private UserRepository repository;
    private CurrentUserProviderImpl provider;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        provider = new CurrentUserProviderImpl(repository, mock(AuthSessionRepository.class));
    }

    @Test
    void deletedAccountDoesNotLoadOrReuseMemberships() {
        assertNull(provider.findById("u"));
        verify(repository, never()).findTeamRolesByUserId(anyString());
    }

    @Test
    void loadsCurrentAccountAndMembershipsForEveryCall() {
        when(repository.findById("u")).thenReturn(user("SUPER_ADMIN"), user("USER"));
        when(repository.findTeamRolesByUserId("u")).thenReturn(
                Arrays.asList(member("a"), member("b")), Collections.singletonList(member("b")));
        CurrentUser before = provider.findById("u");
        CurrentUser after = provider.findById("u");
        assertEquals("SUPER_ADMIN", before.getGlobalRole());
        assertEquals(Arrays.asList("a", "b"), before.getTeamIds());
        assertEquals("USER", after.getGlobalRole());
        assertEquals(Collections.singletonList("b"), after.getTeamIds());
        verify(repository, times(2)).findById("u");
        verify(repository, times(2)).findTeamRolesByUserId("u");
    }

    @Test
    void noMembershipsIsValidButDoesNotGrantAnyTeam() {
        when(repository.findById("u")).thenReturn(user("USER"));
        when(repository.findTeamRolesByUserId("u")).thenReturn(Collections.emptyList());
        CurrentUser current = provider.findById("u");
        assertEquals("u", current.getUserId());
        assertEquals("current-name", current.getUsername());
        assertTrue(current.getTeamIds().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "TEAM_ADMIN"})
    void legacyGlobalRolesRetainOnlyOrdinaryAccountCapabilities(String role) {
        when(repository.findById("u")).thenReturn(user(role));
        when(repository.findTeamRolesByUserId("u")).thenReturn(Collections.singletonList(member("a")));
        assertEquals("USER", provider.findById("u").getGlobalRole());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "super_admin", " SUPER_ADMIN"})
    void invalidRoleCannotAuthenticateOrFallBackToOldClaims(String role) {
        when(repository.findById("u")).thenReturn(user(role));
        assertNull(provider.findById("u"));
        verify(repository, never()).findTeamRolesByUserId(anyString());
    }

    @Test
    void disabledAccountNeverLoadsMemberships() {
        User disabled = user("SUPER_ADMIN"); disabled.setStatus("DISABLED");
        when(repository.findById("u")).thenReturn(disabled);
        assertNull(provider.findById("u"));
        verify(repository, never()).findTeamRolesByUserId(anyString());
    }

    @Test
    void ignoresInvalidTeamIdsAndDeduplicatesTheSnapshot() {
        when(repository.findById("u")).thenReturn(user("USER"));
        when(repository.findTeamRolesByUserId("u")).thenReturn(
                Arrays.asList(member("a"), member(null), member(""), member("a"), member("b")));
        CurrentUser current = provider.findById("u");
        assertEquals(Arrays.asList("a", "b"), current.getTeamIds());
        assertThrows(UnsupportedOperationException.class, () -> current.getTeamIds().add("injected"));
    }

    @Test
    void snapshotCopiesCallerOwnedTeamList() {
        List<String> teams = new ArrayList<String>(Collections.singletonList("a"));
        CurrentUser current = new CurrentUser("u", "name", "USER", teams);
        teams.add("b");
        assertEquals(Collections.singletonList("a"), current.getTeamIds());
    }

    @Test
    void accountLookupFailureIsPropagated() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("unavailable");
        when(repository.findById("u")).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> provider.findById("u")));
    }

    @Test
    void membershipLookupFailureIsPropagatedEvenForSuperAdmin() {
        when(repository.findById("u")).thenReturn(user("SUPER_ADMIN"));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("unavailable");
        when(repository.findTeamRolesByUserId("u")).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> provider.findById("u")));
    }

    private User user(String role) {
        User user = new User();
        user.setId("u");
        user.setUsername("current-name");
        user.setGlobalRole(role);
        user.setPasswordHash("must-not-be-exposed");
        return user;
    }

    private UserTeam member(String teamId) {
        return new UserTeam("u", teamId, "MEMBER");
    }
}
