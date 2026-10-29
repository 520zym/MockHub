package com.mockhub.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.auth.controller.AuthController;
import com.mockhub.auth.controller.MeController;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.mcp.repository.McpTokenRepository;
import com.mockhub.common.filter.JwtAuthFilter;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.common.util.PasswordUtil;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.CurrentUserProviderImpl;
import com.mockhub.system.service.UserServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real signed tokens, Spring transactions, HTTP contracts and temporary SQLite only. */
class AuthSessionIntegrationTest {
    @TempDir Path tempDir;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private JwtUtil jwt;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setUp() {
        SecurityContextHolder.clearContext();
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("auth.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        String hash = PasswordUtil.hash("old-password");
        for (String user : new String[]{"u", "other", "admin"}) {
            jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,'before','before')", user, user, hash, user,
                    "admin".equals(user) ? "SUPER_ADMIN" : "USER");
        }
        for (String team : new String[]{"a", "b", "c", "hidden"}) {
            jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES(?,?,?,'before')", team, team, team);
        }
        jdbc.update("INSERT INTO user_team VALUES('u','a','MEMBER'),('u','b','VIEWER'),('u','c','TEAM_ADMIN'),"
                + "('admin','b','VIEWER')");
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> ds);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.register(Config.class); context.refresh();
        jwt = context.getBean(JwtUtil.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwt, mapper, new CurrentUserProviderImpl(
                context.getBean(UserRepository.class), context.getBean(AuthSessionRepository.class)));
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(AuthController.class), context.getBean(MeController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
    }

    @AfterEach void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) context.close();
    }

    @Test void loginAndMeShareLiveMembershipAndCapabilitiesContract() throws Exception {
        String token = login("u");
        JsonNode me = me(token);
        assertEquals("ACTIVE", me.path("status").asText());
        assertEquals(3, me.path("teams").size());
        JsonNode editor = team(me, "a"), viewer = team(me, "b"), admin = team(me, "c");
        assertEquals("EDITOR", editor.path("role").asText());
        assertEquals("EDITOR", editor.path("membershipRole").asText());
        assertEquals("MEMBER", editor.path("accessMode").asText());
        assertTrue(editor.path("capabilities").path("write").asBoolean());
        assertFalse(editor.path("capabilities").path("batchDelete").asBoolean());
        assertFalse(viewer.path("capabilities").path("write").asBoolean());
        assertTrue(admin.path("capabilities").path("manageMembers").asBoolean());
        assertEquals(7, admin.path("capabilities").size());
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        jdbc.update("UPDATE user_team SET role='EDITOR' WHERE user_id='u' AND team_id='b'");
        JsonNode changed = me(token);
        assertEquals(2, changed.path("teams").size());
        assertTrue(team(changed, "b").path("capabilities").path("write").asBoolean());
        assertFalse(changed.has("passwordHash")); assertFalse(changed.has("authVersion"));
    }

    @Test void superAdminListsAllTeamsWithoutPretendingToBeAMember() throws Exception {
        String token = login("admin");
        JsonNode me = me(token);
        assertEquals(4, me.path("teams").size());
        JsonNode hidden = team(me, "hidden");
        assertTrue(hidden.path("role").isNull()); assertTrue(hidden.path("membershipRole").isNull());
        assertEquals("SYSTEM_MAINTENANCE", hidden.path("accessMode").asText());
        assertTrue(hidden.path("capabilities").path("purge").asBoolean());
        assertEquals("VIEWER", team(me, "b").path("role").asText());
        assertEquals("MEMBER", team(me, "b").path("accessMode").asText());
        jdbc.update("UPDATE user SET global_role='USER' WHERE id='admin'");
        assertEquals(1, me(token).path("teams").size());
    }

    @Test void profileChangesOnlySelfDisplayNameWithoutRoleOrCredentialSideEffects() throws Exception {
        String token = login("u");
        mvc.perform(patch("/api/me/profile").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\" New Name \",\"globalRole\":\"SUPER_ADMIN\",\"username\":\"other\",\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.displayName").value("New Name"));
        assertEquals("New Name", me(token).path("displayName").asText());
        assertEquals("USER", me(token).path("globalRole").asText());
        assertEquals("u", me(token).path("username").asText());
        assertEquals(0, jdbc.queryForObject("SELECT auth_version FROM user WHERE id='u'", Integer.class));
        mvc.perform(patch("/api/me/profile").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"   \"}")).andExpect(jsonPath("$.code").value(40000));
    }

    @Test void logoutRevokesOnlyCurrentDeviceAndLeavesMcpUntouched() throws Exception {
        String one = login("u"), two = login("u");
        seedMcp();
        JsonNode list = sessions(one);
        assertEquals(2, list.size());
        assertEquals(1, countCurrent(list));
        assertFalse(list.get(0).has("authVersion"));
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(one)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        denied(one); assertEquals("u", me(two).path("id").asText());
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='mcp'", String.class));
    }

    @Test void deviceRevocationCannotCrossAccountBoundary() throws Exception {
        String one = login("u"), two = login("u"), other = login("other");
        String target = jwt.validateToken(two).get("sid", String.class);
        String otherSid = jwt.validateToken(other).get("sid", String.class);
        mvc.perform(delete("/api/me/sessions/" + otherSid).header("Authorization", bearer(one)))
                .andExpect(jsonPath("$.code").value(40401));
        assertEquals("other", me(other).path("id").asText());
        mvc.perform(delete("/api/me/sessions/" + target).header("Authorization", bearer(one)))
                .andExpect(jsonPath("$.code").value(0));
        denied(two); assertEquals(1, sessions(one).size());
    }

    @Test void allWebLogoutIncrementsVersionButNeverRevokesExternalMcp() throws Exception {
        String one = login("u"), two = login("u"); seedMcp();
        mvc.perform(post("/api/me/revoke-web-sessions").header("Authorization", bearer(one)))
                .andExpect(jsonPath("$.code").value(0));
        denied(one); denied(two);
        assertEquals(1, jdbc.queryForObject("SELECT auth_version FROM user WHERE id='u'", Integer.class));
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='mcp'", String.class));
        assertEquals("u", me(login("u")).path("id").asText());
    }

    @Test void disabledMissingExpiredAndPreUpgradeCredentialsAreRejected() throws Exception {
        String one = login("u");
        jdbc.update("UPDATE user SET status='DISABLED' WHERE id='u'"); denied(one);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"password\":\"old-password\"}"))
                .andExpect(jsonPath("$.code").value(40001));
        jdbc.update("UPDATE user SET status='ACTIVE' WHERE id='u'");
        jdbc.update("UPDATE auth_session SET expires_at=0 WHERE user_id='u'"); denied(one);
        String current = login("u"); jdbc.update("DELETE FROM user WHERE id='u'"); denied(current);
        denied(jwt.generateToken("other", "other", "SUPER_ADMIN", Collections.singletonList("hidden")));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"missing\",\"password\":\"old-password\"}"))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test void selfPasswordChangeRevokesEveryWebSessionAndOnlyOwnersMcp() throws Exception {
        String one = login("u"), two = login("u"); seedMcp();
        jdbc.update("INSERT INTO mcp_token(id,name,token_hash,token_prefix,user_id,created_at) "
                + "VALUES('other-mcp','Other','other-hash','prefix','other','now')");
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(one))
                .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(jsonPath("$.code").value(0));
        denied(one); denied(two);
        assertEquals(1, jdbc.queryForObject("SELECT auth_version FROM user WHERE id='u'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT first_login FROM user WHERE id='u'", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='mcp'", String.class));
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='other-mcp'", String.class));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"password\":\"old-password\"}"))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"password\":\"new-password\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.user.firstLogin").value(false));
    }

    @Test void selfPasswordChangeRollsBackEveryCredentialWhenFirstLoginWriteFails() throws Exception {
        String token = login("u"); seedMcp();
        String hash = jdbc.queryForObject("SELECT password_hash FROM user WHERE id='u'", String.class);
        jdbc.execute("CREATE TRIGGER reject_first_login BEFORE UPDATE OF first_login ON user "
                + "BEGIN SELECT RAISE(ABORT, 'first_login write failed'); END");
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(jsonPath("$.code").value(50001));
        assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM user WHERE id='u'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT auth_version FROM user WHERE id='u'", Integer.class));
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='mcp'", String.class));
        assertEquals("u", me(token).path("id").asText());
    }

    @Test void wrongPasswordAndInvalidNewPasswordNeverInvalidateCredentials() throws Exception {
        String token = login("u"); seedMcp();
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"wrong\",\"newPassword\":\"new-password\"}"))
                .andExpect(jsonPath("$.code").value(40004));
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"old-password\",\"newPassword\":\"short\"}"))
                .andExpect(jsonPath("$.code").value(40000));
        assertEquals("u", me(token).path("id").asText());
        assertEquals(0, jdbc.queryForObject("SELECT auth_version FROM user WHERE id='u'", Integer.class));
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='mcp'", String.class));
    }

    private String login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").header("User-Agent", "Test browser")
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + username + "\",\"password\":\"old-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = json(result).path("data");
        assertEquals("ACTIVE", data.path("user").path("status").asText());
        return data.path("token").asText();
    }
    private JsonNode me(String token) throws Exception {
        return json(mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn()).path("data");
    }
    private JsonNode sessions(String token) throws Exception {
        return json(mvc.perform(get("/api/me/sessions").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(0)).andReturn()).path("data");
    }
    private void denied(String token) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40002));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private String bearer(String token) { return "Bearer " + token; }
    private JsonNode team(JsonNode me, String id) {
        for (JsonNode team : me.path("teams")) if (id.equals(team.path("teamId").asText())) return team;
        throw new AssertionError("Missing team " + id);
    }
    private int countCurrent(JsonNode list) {
        int count = 0; for (JsonNode item : list) if (item.path("current").asBoolean()) count++; return count;
    }
    private void seedMcp() {
        jdbc.update("INSERT INTO mcp_token(id,name,token_hash,token_prefix,user_id,created_at) VALUES('mcp','MCP','hash','prefix','u','now')");
    }

    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    @Import({AuthService.class, UserServiceImpl.class, UserRepository.class, TeamRepository.class,
            AuthSessionRepository.class, CredentialRevocationService.class, McpTokenRepository.class,
            JwtUtil.class, AuthController.class, MeController.class})
    static class Config {
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
}
