package com.mockhub.system.service;

import com.mockhub.auth.model.ChangePasswordRequest;
import com.mockhub.auth.service.AuthService;
import com.mockhub.auth.service.CredentialRevocationService;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PasswordUtil;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class UserServiceImplResetPasswordTest {

    private UserRepository repository;
    private UserServiceImpl service;
    private CredentialRevocationService revocation;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        repository = mock(UserRepository.class);
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userRepository", repository);
        revocation = mock(CredentialRevocationService.class);
        ReflectionTestUtils.setField(service, "credentialRevocationService", revocation);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "TEAM_ADMIN"})
    void resetShouldRejectUsersBeforeValidationOrAnyUserLookup(String teamRole) {
        signIn("USER", teamRole);

        assertEquals(40101, assertThrows(BizException.class,
                () -> service.resetPassword("target", "new-password")).getCode());
        assertEquals(40101, assertThrows(BizException.class,
                () -> service.resetPassword("missing", null)).getCode());
        assertEquals(40101, assertThrows(BizException.class,
                () -> service.resetPassword("caller", "short")).getCode());
        verifyNoInteractions(repository, revocation);
    }

    @Test
    void resetShouldRejectUnauthenticatedBeforeValidationOrLookup() {
        assertEquals(40002, assertThrows(BizException.class,
                () -> service.resetPassword("target", "new-password")).getCode());
        assertEquals(40002, assertThrows(BizException.class,
                () -> service.resetPassword("missing", null)).getCode());
        verifyNoInteractions(repository, revocation);
    }

    @Test
    void resetShouldAllowSuperAdminAndStoreHashAndFirstLogin() {
        signIn("SUPER_ADMIN", null);
        User target = new User();
        target.setId("target");
        target.setUsername("target-user");
        when(repository.findById("target")).thenReturn(target);

        service.resetPassword("target", "123456");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> updatedAt = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(repository);
        order.verify(repository).findById("target");
        order.verify(repository).updatePassword(eq("target"), hash.capture(), updatedAt.capture());
        order.verify(repository).updateFirstLogin("target", true, updatedAt.getValue());
        verify(revocation).revokeSecurityCredentials("target");
        assertNotEquals("123456", hash.getValue());
        assertTrue(PasswordUtil.verify("123456", hash.getValue()));
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "12345"})
    void resetShouldRejectInvalidPasswordWithoutModifications(String password) {
        signIn("SUPER_ADMIN", null);

        BizException error = assertThrows(BizException.class,
                () -> service.resetPassword("target", password));

        assertEquals(40000, error.getCode());
        verifyNoInteractions(repository, revocation);
    }

    @Test
    void resetShouldRejectMissingUserWithoutModifications() {
        signIn("SUPER_ADMIN", null);

        BizException error = assertThrows(BizException.class,
                () -> service.resetPassword("missing", "new-password"));

        assertEquals(40401, error.getCode());
        verify(repository).findById("missing");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void genericPasswordUpdateShouldRemainAvailableToRegularUserWithoutChangingFirstLogin() {
        signIn("USER", "MEMBER");
        String hash = PasswordUtil.hash("self-password");

        service.updatePassword("caller", hash);

        verify(repository).updatePassword(eq("caller"), eq(hash), anyString());
        verify(revocation).revokeSecurityCredentials("caller");
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void selfPasswordChangeShouldKeepOriginalFirstLoginBehavior(boolean firstLogin) {
        signIn("USER", "MEMBER");
        User caller = new User();
        caller.setId("caller");
        caller.setUsername("caller-user");
        caller.setPasswordHash(PasswordUtil.hash("old-password"));
        caller.setFirstLogin(firstLogin);
        when(repository.findById("caller")).thenReturn(caller);
        AuthService authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userService", service);

        authService.changePassword(new ChangePasswordRequest("old-password", "self-password"));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).findById("caller");
        verify(repository).updatePassword(eq("caller"), hash.capture(), anyString());
        assertTrue(PasswordUtil.verify("self-password", hash.getValue()));
        verify(revocation).revokeSecurityCredentials("caller");
        if (firstLogin) {
            verify(repository).updateFirstLogin(eq("caller"), eq(false), anyString());
        } else {
            verify(repository, never()).updateFirstLogin(anyString(), anyBoolean(), anyString());
        }
        verifyNoMoreInteractions(repository);
    }

    private void signIn(String globalRole, String teamRole) {
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", "caller");
        details.put("username", "caller-user");
        details.put("globalRole", globalRole);
        details.put("teamIds", Collections.singletonList("team-1"));
        details.put("teamRoles", Collections.singletonMap("team-1", teamRole));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "caller-user", null, Collections.emptyList());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
