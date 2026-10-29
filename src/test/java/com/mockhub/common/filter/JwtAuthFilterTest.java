package com.mockhub.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.CurrentUser;
import com.mockhub.common.util.CurrentUserProvider;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.common.util.SecurityContextUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private JwtUtil jwtUtil;
    private CurrentUserProvider users;
    private JwtAuthFilter filter;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtUtil = mock(JwtUtil.class);
        users = mock(CurrentUserProvider.class);
        mapper = new ObjectMapper();
        filter = new JwtAuthFilter(jwtUtil, mapper, users);
        Claims claims = Jwts.claims().setSubject("user-1");
        claims.put("username", "old-name");
        claims.put("globalRole", "SUPER_ADMIN");
        claims.put("teamIds", "old-team");
        when(jwtUtil.validateToken("old-token")).thenReturn(claims);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sameTokenUsesCurrentIdentityAndTeamsOnEveryRequest() throws Exception {
        when(users.findById("user-1")).thenReturn(
                new CurrentUser("user-1", "current-name", "SUPER_ADMIN", Arrays.asList("a", "b")),
                new CurrentUser("user-1", "renamed", "USER", Collections.singletonList("b")));
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/apis", "Bearer old-token"), response, (req, res) -> {
                int call = calls.getAndIncrement();
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                assertEquals("user-1", auth.getPrincipal());
                assertTrue(auth.isAuthenticated());
                assertNull(auth.getCredentials());
                assertEquals(call == 0 ? "current-name" : "renamed", SecurityContextUtil.getCurrentUsername());
                assertEquals(call == 0 ? "SUPER_ADMIN" : "USER", SecurityContextUtil.getCurrentGlobalRole());
                assertEquals(call == 0 ? Arrays.asList("a", "b") : Collections.singletonList("b"),
                        SecurityContextUtil.getCurrentTeamIds());
                assertEquals(call == 0 ? "ROLE_SUPER_ADMIN" : "ROLE_USER",
                        auth.getAuthorities().iterator().next().getAuthority());
            });
            assertEquals(200, response.getStatus());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        assertEquals(2, calls.get());
        verify(users, times(2)).findById("user-1");
    }

    @Test
    void currentAccountWithNoTeamsRemainsAuthenticatedWithoutStaleTeamClaims() throws Exception {
        when(users.findById("user-1")).thenReturn(currentUser());
        filter.doFilter(request("/api/apis", "Bearer old-token"), new MockHttpServletResponse(), (req, res) -> {
            assertEquals(Collections.emptyList(), SecurityContextUtil.getCurrentTeamIds());
            assertFalse(SecurityContextUtil.isSuperAdmin());
        });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void deletedAccountIsRejectedEvenWithValidSignedIdentity() throws Exception {
        when(users.findById("user-1")).thenReturn(null);
        assertUnauthorized("Bearer old-token");
        verify(users).findById("user-1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Basic old-token", "Bearer ", "Bearer invalid-token", "bearer old-token"})
    void missingOrInvalidBearerDoesNotLoadDatabaseIdentity(String header) throws Exception {
        assertUnauthorized(header);
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingSubjectIsRejectedBeforeDatabaseLookup(String subject) throws Exception {
        when(jwtUtil.validateToken("old-token")).thenReturn(Jwts.claims().setSubject(subject));
        assertUnauthorized("Bearer old-token");
        verifyNoInteractions(users);
    }

    @Test
    void databaseFailureDoesNotFallBackToTokenClaimsOrLeavePriorIdentity() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(users.findById("user-1")).thenThrow(failure);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("previous-admin", null, Collections.emptyList()));
        FilterChain chain = mock(FilterChain.class);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> filter.doFilter(request("/api/apis", "Bearer old-token"), new MockHttpServletResponse(), chain)));
        verifyNoInteractions(chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    static Stream<Exception> downstreamFailures() {
        return Stream.of(new ServletException("controller failure"), new IOException("write failure"),
                new IllegalStateException("business failure"));
    }

    @ParameterizedTest
    @MethodSource("downstreamFailures")
    void downstreamFailureIsNotRewrittenAsAuthenticationFailure(Exception failure) {
        when(users.findById("user-1")).thenReturn(currentUser());
        MockHttpServletResponse response = new MockHttpServletResponse();
        Exception thrown = assertThrows(Exception.class, () -> filter.doFilter(
                request("/api/apis", "Bearer old-token"), response, (req, res) -> {
                    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
                    if (failure instanceof ServletException) throw (ServletException) failure;
                    if (failure instanceof IOException) throw (IOException) failure;
                    throw (RuntimeException) failure;
                }));
        assertSame(failure, thrown);
        assertNotEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mock/PAY/orders/1", "/mock/PAY/service", "/api/auth/login", "/api/health",
            "/api/server-address", "/", "/index.html", "/assets/app.js", "/favicon.ico", "/files/file-id", "/mcp"})
    void publicPathsStillBypassJwtAndDatabaseChecks(String path) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        for (String header : Arrays.asList(null, "Bearer invalid-token", "Bearer deleted-user-token")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockHttpServletRequest request = request(path, header);
            if (path.equals("/mock/PAY/service")) request.setQueryString("wsdl");
            filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
            assertEquals(200, response.getStatus());
        }
        assertEquals(3, calls.get());
        verifyNoInteractions(jwtUtil, users);
    }

    private CurrentUser currentUser() {
        return new CurrentUser("user-1", "current-name", "USER", Collections.emptyList());
    }

    private void assertUnauthorized(String header) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("previous-admin", null, Collections.emptyList()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("/api/apis", header), response, chain);
        assertEquals(401, response.getStatus());
        assertEquals(40002, mapper.readTree(response.getContentAsString()).path("code").asInt());
        verifyNoInteractions(chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String path, String header) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (header != null) request.addHeader("Authorization", header);
        return request;
    }
}
