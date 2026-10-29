package com.mockhub.mcp.service;

import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.auth.service.CredentialRevocationService;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mcp.repository.McpTokenRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.CurrentUserProviderImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class McpAuthAccountStateTest {
    @TempDir Path tempDir;
    private JdbcTemplate jdbc;
    private McpAuthService auth;
    private CredentialRevocationService revocation;
    private UserRepository users;
    private TransactionTemplate transaction;

    @BeforeEach void setUp() {
        SecurityContextHolder.clearContext();
        SQLiteDataSource ds = new SQLiteDataSource(); ds.setUrl("jdbc:sqlite:" + tempDir.resolve("mcp-auth.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) "
                + "VALUES('u','owner','hash','Owner','SUPER_ADMIN','before','before')");
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('t','Team','TEAM','before')");
        jdbc.update("INSERT INTO user_team VALUES('u','t','TEAM_ADMIN')");
        jdbc.update("INSERT INTO mcp_token(id,name,token_hash,token_prefix,user_id,created_at) "
                + "VALUES('mcp','MCP',?,'mhk_test','u','before')", McpTokenService.sha256("mhk_test"));
        users = new UserRepository(); ReflectionTestUtils.setField(users, "jdbcTemplate", jdbc);
        AuthSessionRepository sessions = new AuthSessionRepository(jdbc);
        McpTokenRepository tokens = new McpTokenRepository(jdbc);
        auth = new McpAuthService(new McpTokenService(tokens), new CurrentUserProviderImpl(users, sessions));
        revocation = new CredentialRevocationService(users, sessions, tokens);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

    @Test void eachMcpCallUsesCurrentOwnerRoleTeamsAndStatus() {
        auth.authenticate(request()); assertTrue(SecurityContextUtil.isSuperAdmin());
        jdbc.update("UPDATE user SET global_role='USER' WHERE id='u'");
        jdbc.update("DELETE FROM user_team WHERE user_id='u'");
        auth.authenticate(request()); assertFalse(SecurityContextUtil.isSuperAdmin());
        assertTrue(SecurityContextUtil.getCurrentTeamIds().isEmpty());
        jdbc.update("UPDATE user SET status='DISABLED' WHERE id='u'");
        assertEquals(40101, assertThrows(BizException.class, () -> auth.authenticate(request())).getCode());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        jdbc.update("DELETE FROM user WHERE id='u'");
        assertThrows(BizException.class, () -> auth.authenticate(request()));
    }

    @Test void securityDisablePrimitiveRevokesOwnerTokensPermanentlyEvenAfterRestore() {
        transaction.execute(status -> {
            users.updateStatus("u", "DISABLED", "now");
            revocation.revokeSecurityCredentials("u"); return null;
        });
        assertEquals(1, users.findById("u").getAuthVersion());
        assertNotNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='mcp'", String.class));
        users.updateStatus("u", "ACTIVE", "later");
        assertThrows(BizException.class, () -> auth.authenticate(request()));
    }

    @Test void securityDisableRollsBackStatusAndVersionIfMcpRevocationFails() {
        jdbc.execute("CREATE TRIGGER reject_mcp_revoke BEFORE UPDATE OF revoked_at ON mcp_token "
                + "BEGIN SELECT RAISE(ABORT,'MCP revoke failed'); END");
        assertThrows(DataAccessException.class, () -> transaction.execute(status -> {
            users.updateStatus("u", "DISABLED", "now");
            revocation.revokeSecurityCredentials("u"); return null;
        }));
        assertTrue(users.findById("u").isActive()); assertEquals(0, users.findById("u").getAuthVersion());
        assertNotNull(auth.authenticate(request()));
    }

    @Test void databaseFailureNeverKeepsPreviousAdminOrSilentlyUsesEmptyTeams() {
        auth.authenticate(request()); jdbc.execute("DROP TABLE user_team");
        assertThrows(DataAccessException.class, () -> auth.authenticate(request()));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "previous-admin", null, Collections.emptyList()));
        assertThrows(BizException.class, () -> auth.authenticate(new MockHttpServletRequest()));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer mhk_test"); return request;
    }
}
