package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.enums.TeamRoleType;
import com.mockhub.system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamPermissionMatrixTest {
    private UserRepository users;
    private PermissionCheckerImpl checker;

    @BeforeEach void setUp() {
        users = mock(UserRepository.class);
        checker = new PermissionCheckerImpl();
        ReflectionTestUtils.setField(checker, "userRepository", users);
        login("USER");
    }
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"VIEWER", "EDITOR", "TEAM_ADMIN", "MEMBER"})
    void allValidTeamRolesCanRead(String role) {
        when(users.findUserTeamRole("u", "a")).thenReturn(role);
        assertDoesNotThrow(() -> checker.checkTeamAccess("a"));
    }

    @ParameterizedTest @ValueSource(strings={"EDITOR", "TEAM_ADMIN", "MEMBER"})
    void editorsCanWriteWithoutBeingResourceCreator(String role) {
        when(users.findUserTeamRole("u", "a")).thenReturn(role);
        assertDoesNotThrow(() -> checker.checkTeamWrite("a"));
    }

    @Test void viewerCannotWriteOrAdminister() {
        when(users.findUserTeamRole("u", "a")).thenReturn("VIEWER");
        assertEquals(40104, assertThrows(BizException.class, () -> checker.checkTeamWrite("a")).getCode());
        assertEquals(40101, assertThrows(BizException.class, () -> checker.checkTeamAdmin("a")).getCode());
        assertEquals(40101, assertThrows(BizException.class, checker::checkSystemAdmin).getCode());
    }

    @ParameterizedTest @ValueSource(strings={"EDITOR", "MEMBER"})
    void editorCannotAdministerTeamOrPlatform(String role) {
        when(users.findUserTeamRole("u", "a")).thenReturn(role);
        assertEquals(40101, assertThrows(BizException.class, () -> checker.checkTeamAdmin("a")).getCode());
        assertEquals(40101, assertThrows(BizException.class, checker::checkSystemAdmin).getCode());
    }

    @Test void teamAdminIsNotSystemAdminAndCannotWriteOtherTeam() {
        when(users.findUserTeamRole("u", "a")).thenReturn("TEAM_ADMIN");
        when(users.findUserTeamRole("u", "b")).thenReturn("VIEWER");
        assertDoesNotThrow(() -> checker.checkTeamAdmin("a"));
        assertEquals(40104, assertThrows(BizException.class, () -> checker.checkTeamWrite("b")).getCode());
        assertThrows(BizException.class, checker::checkSystemAdmin);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"SUPER_ADMIN", "INVALID", "editor"})
    void absentOrUnknownMembershipFailsClosed(String role) {
        when(users.findUserTeamRole("u", "a")).thenReturn(role);
        assertEquals(40102, assertThrows(BizException.class, () -> checker.checkTeamAccess("a")).getCode());
        assertEquals(40102, assertThrows(BizException.class, () -> checker.checkTeamWrite("a")).getCode());
        assertThrows(BizException.class, () -> checker.checkTeamAdmin("a"));
    }

    @Test void roleChangesAreReadOnEachOperation() {
        when(users.findUserTeamRole("u", "a")).thenReturn("EDITOR", "VIEWER", null);
        checker.checkTeamWrite("a");
        assertEquals(40104, assertThrows(BizException.class, () -> checker.checkTeamWrite("a")).getCode());
        assertEquals(40102, assertThrows(BizException.class, () -> checker.checkTeamAccess("a")).getCode());
    }

    @Test void superAdminMaintainsTeamsWithoutFakeMembership() {
        login("SUPER_ADMIN");
        checker.checkTeamAccess("a"); checker.checkTeamWrite("a"); checker.checkTeamAdmin("a");
        checker.checkSystemAdmin();
        verifyNoInteractions(users);
    }

    @Test void legacyMemberMappingNeverGrantsAdministration() {
        assertEquals(TeamRoleType.EDITOR, TeamRoleType.fromStored("MEMBER"));
        assertFalse(TeamRoleType.fromStored("MEMBER").canAdminister());
        assertNull(TeamRoleType.fromStored("SUPER_ADMIN"));
    }

    private void login(String globalRole) {
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", "u"); details.put("globalRole", globalRole);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", null, Collections.emptyList());
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
