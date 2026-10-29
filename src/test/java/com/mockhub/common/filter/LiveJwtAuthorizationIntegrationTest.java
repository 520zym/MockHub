package com.mockhub.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.common.util.PasswordUtil;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.auth.model.AuthSession;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.ApiTagRepository;
import com.mockhub.mock.repository.GroupRepository;
import com.mockhub.mock.repository.TagRepository;
import com.mockhub.mock.service.ApiServiceImpl;
import com.mockhub.mock.service.MockFileStorageService;
import com.mockhub.stats.repository.StatsRepository;
import com.mockhub.stats.service.StatsServiceImpl;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.controller.UserController;
import com.mockhub.system.service.UserServiceImpl;
import com.mockhub.system.service.CurrentUserProviderImpl;
import com.mockhub.system.service.PermissionCheckerImpl;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;

import javax.servlet.FilterChain;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实签名 JWT + 临时 SQLite 的回归，不启动应用、不连接工作区 data 目录。
 */
class LiveJwtAuthorizationIntegrationTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private JwtUtil jwt;
    private JwtAuthFilter filter;
    private ApiServiceImpl apis;
    private ApiRepository apiRepository;
    private PermissionCheckerImpl permission;
    private UserRepository users;
    private String token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("authorization.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO user(id, username, password_hash, display_name, global_role, created_at, updated_at) "
                        + "VALUES('u', 'current-name', 'unused-test-hash', 'Current User', 'USER', ?, ?)",
                "2026-10-29T00:00:00", "2026-10-29T00:00:00");
        for (String teamId : Arrays.asList("a", "b")) {
            jdbc.update("INSERT INTO team(id, name, identifier, created_at) VALUES(?, ?, ?, ?)",
                    teamId, "Team " + teamId, teamId.toUpperCase(), "2026-10-29T00:00:00");
            jdbc.update("INSERT INTO user_team(user_id, team_id, role) VALUES('u', ?, 'MEMBER')", teamId);
            jdbc.update("INSERT INTO api_definition(id, team_id, name, method, path, created_at, updated_at) "
                            + "VALUES(?, ?, ?, 'GET', ?, ?, ?)",
                    "api-" + teamId, teamId, "API " + teamId, "/" + teamId,
                    "2026-10-29T00:00:00", "2026-10-29T00:00:00");
        }
        users = new UserRepository();
        ReflectionTestUtils.setField(users, "jdbcTemplate", jdbc);
        permission = new PermissionCheckerImpl();
        ReflectionTestUtils.setField(permission, "userRepository", users);
        jwt = new JwtUtil();
        jwt.init();
        AuthSessionRepository sessions = new AuthSessionRepository(jdbc);
        AuthSession session = new AuthSession(); session.setId("test-session"); session.setUserId("u");
        session.setCreatedAt("now"); session.setExpiresAt(System.currentTimeMillis() + JwtUtil.EXPIRATION_MS);
        assertTrue(sessions.insertIfCurrent(session));
        filter = new JwtAuthFilter(jwt, new ObjectMapper(), new CurrentUserProviderImpl(users, sessions));
        token = jwt.generateToken("u", "old-name", "USER", Arrays.asList("a", "b", "removed"), 0L, "test-session");

        apiRepository = spy(new ApiRepository(jdbc));
        ApiTagRepository apiTags = mock(ApiTagRepository.class);
        ApiResponseRepository responses = mock(ApiResponseRepository.class);
        when(apiTags.findTagIdsByApiId(anyString())).thenReturn(Collections.emptyList());
        when(responses.findSummaryByApiId(anyString())).thenReturn(Collections.emptyList());
        apis = new ApiServiceImpl(apiRepository, responses, apiTags, mock(GroupRepository.class),
                mock(TagRepository.class), mock(TeamService.class), permission, new ObjectMapper(),
                mock(LogService.class), users, mock(MockFileStorageService.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void removedTeamIsExcludedFromItemsAndTotalOnNextRequestWithSameToken() throws Exception {
        assertEquals(2L, listWithToken(null).getTotal());
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        for (String teamId : Arrays.asList(null, "")) {
            PageResult<ApiDefinitionVO> page = listWithToken(teamId);
            assertEquals(1L, page.getTotal());
            assertEquals(1, page.getItems().size());
            assertEquals("b", page.getItems().get(0).getTeamId());
        }
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void removingAllTeamsReturnsEmptyInsteadOfUnrestrictedRepositoryQuery() throws Exception {
        jdbc.update("DELETE FROM user_team WHERE user_id='u'");
        PageResult<ApiDefinitionVO> page = listWithToken(null);
        assertEquals(0L, page.getTotal());
        assertTrue(page.getItems().isEmpty());
        verifyNoInteractions(apiRepository);
    }

    @Test
    void explicitRemovedTeamAndItsDetailsAreDeniedDespiteOldTokenClaims() {
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        for (boolean detail : Arrays.asList(false, true)) {
            BizException failure = assertThrows(BizException.class,
                    () -> filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> {
                        if (detail) apis.getById("api-a"); else list("a");
                    }));
            assertEquals(40102, failure.getCode());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Test
    void explicitCurrentTeamStillWorks() throws Exception {
        PageResult<ApiDefinitionVO> page = listWithToken("b");
        assertEquals(1L, page.getTotal());
        assertEquals("b", page.getItems().get(0).getTeamId());
    }

    @Test
    void oldSuperAdminTokenCannotPreserveUnrestrictedAccessAfterDemotion() throws Exception {
        jdbc.update("UPDATE user SET global_role='SUPER_ADMIN' WHERE id='u'");
        token = jwt.generateToken("u", "old-admin", "SUPER_ADMIN", Arrays.asList("a", "b"), 0L, "test-session");
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        assertEquals(2L, listWithToken(null).getTotal(), "current super-admin keeps cross-team maintenance");

        jdbc.update("UPDATE user SET global_role='USER' WHERE id='u'");
        PageResult<ApiDefinitionVO> page = listWithToken(null);
        assertEquals(1L, page.getTotal());
        assertEquals("b", page.getItems().get(0).getTeamId());
        filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> {
            assertFalse(SecurityContextUtil.isSuperAdmin());
            assertEquals("ROLE_USER", SecurityContextHolder.getContext().getAuthentication()
                    .getAuthorities().iterator().next().getAuthority());
            assertEquals(40101, assertThrows(BizException.class, () -> permission.checkTeamAdmin("b")).getCode());
            StatsRepository stats = mock(StatsRepository.class);
            assertEquals(40103, assertThrows(BizException.class,
                    () -> new StatsServiceImpl(stats, users).getOverview(null, 7)).getCode());
            verifyNoInteractions(stats);
        });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void deletingAccountRejectsSameTokenBeforeAnyBusinessQuery() throws Exception {
        jdbc.update("DELETE FROM user WHERE id='u'");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, chain);
        assertEquals(401, response.getStatus());
        assertEquals(40002, new ObjectMapper().readTree(response.getContentAsString()).path("code").asInt());
        verifyNoInteractions(chain, apiRepository);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void serviceAlsoIgnoresStaleTeamIdsInNonWebContext() {
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        Map<String, Object> staleDetails = new HashMap<String, Object>();
        staleDetails.put("userId", "u");
        staleDetails.put("username", "old-name");
        staleDetails.put("globalRole", "USER");
        staleDetails.put("teamIds", Collections.singletonList("a"));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("u", null, Collections.emptyList());
        auth.setDetails(staleDetails);
        SecurityContextHolder.getContext().setAuthentication(auth);
        PageResult<ApiDefinitionVO> page = list(null);
        assertEquals(1L, page.getTotal());
        assertEquals("b", page.getItems().get(0).getTeamId());
    }

    @Test
    void resetPasswordEndpointUsesLiveRoleAndRealServiceAuthorization() throws Exception {
        UserServiceImpl userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "userRepository", users);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
        for (String teamRole : Arrays.asList("MEMBER", "TEAM_ADMIN")) {
            jdbc.update("UPDATE user_team SET role=? WHERE user_id='u'", teamRole);
            mvc.perform(post("/api/users/u/reset-password").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"new-password\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40101));
            assertEquals("unused-test-hash", users.findById("u").getPasswordHash());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        jdbc.update("UPDATE user SET global_role='SUPER_ADMIN' WHERE id='u'");
        mvc.perform(post("/api/users/u/reset-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertTrue(PasswordUtil.verify("new-password", users.findById("u").getPasswordHash()));
        assertTrue(users.findById("u").isFirstLogin());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private PageResult<ApiDefinitionVO> listWithToken(String teamId) throws Exception {
        AtomicReference<PageResult<ApiDefinitionVO>> result = new AtomicReference<PageResult<ApiDefinitionVO>>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, (req, res) -> result.set(list(teamId)));
        assertEquals(200, response.getStatus());
        assertNotNull(result.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        return result.get();
    }

    private PageResult<ApiDefinitionVO> list(String teamId) {
        return apis.list(teamId, null, null, null, null, Collections.emptyList(), null, null, null, 1, 20);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/apis");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
