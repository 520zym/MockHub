package com.mockhub.system.service;

import com.mockhub.common.util.PasswordUtil;
import com.mockhub.auth.service.CredentialRevocationService;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.mcp.repository.McpTokenRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceImplResetPasswordTransactionTest {

    @TempDir
    Path tempDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbcTemplate;
    private UserRepository repository;
    private UserServiceImpl service;
    private String oldHash;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("reset-password.db").toString());
        jdbcTemplate = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        oldHash = PasswordUtil.hash("old-password");
        jdbcTemplate.update("INSERT INTO user (id,username,password_hash,display_name,global_role,first_login,created_at,updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "target", "target-user", oldHash, "Target", "USER", 0, "before", "before");
        jdbcTemplate.update("INSERT INTO auth_session(id,user_id,auth_version,created_at,expires_at) VALUES('session','target',0,'before',?)",
                System.currentTimeMillis() + 100000);
        jdbcTemplate.update("INSERT INTO mcp_token(id,name,token_hash,token_prefix,user_id,created_at) "
                + "VALUES('mcp','MCP','hash','prefix','target','before')");
        repository = new UserRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> jdbcTemplate);
        context.registerBean(UserRepository.class, () -> repository);
        context.register(TransactionConfig.class);
        context.refresh();
        service = context.getBean(UserServiceImpl.class);
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", "admin");
        details.put("username", "admin");
        details.put("globalRole", "SUPER_ADMIN");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", null, Collections.emptyList());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void resetShouldCommitHashAndFirstLoginThroughSpringTransactionProxy() {
        assertTrue(AopUtils.isCglibProxy(service));

        service.resetPassword("target", "new-password");

        User updated = repository.findById("target");
        assertNotEquals(oldHash, updated.getPasswordHash());
        assertTrue(PasswordUtil.verify("new-password", updated.getPasswordHash()));
        assertTrue(updated.isFirstLogin());
        assertNotEquals("before", updated.getUpdatedAt());
        assertEquals(1, updated.getAuthVersion());
        assertTrue(credentialsRevoked());
    }

    @Test
    void resetShouldRollBackHashWhenFirstLoginWriteFails() {
        jdbcTemplate.execute("CREATE TRIGGER reject_first_login BEFORE UPDATE OF first_login ON user "
                + "BEGIN SELECT RAISE(ABORT, 'first_login write failed'); END");

        assertThrows(DataAccessException.class, () -> service.resetPassword("target", "new-password"));

        User unchanged = repository.findById("target");
        assertEquals(oldHash, unchanged.getPasswordHash());
        assertFalse(unchanged.isFirstLogin());
        assertEquals("before", unchanged.getUpdatedAt());
        assertEquals(0, unchanged.getAuthVersion());
        assertFalse(credentialsRevoked());
    }

    @Test
    void mcpRevocationFailureRollsBackPasswordVersionSessionsAndFirstLogin() {
        jdbcTemplate.execute("CREATE TRIGGER reject_mcp_revoke BEFORE UPDATE OF revoked_at ON mcp_token "
                + "BEGIN SELECT RAISE(ABORT, 'MCP revoke failed'); END");
        assertThrows(DataAccessException.class, () -> service.resetPassword("target", "new-password"));
        User unchanged = repository.findById("target");
        assertEquals(oldHash, unchanged.getPasswordHash());
        assertFalse(unchanged.isFirstLogin()); assertEquals(0, unchanged.getAuthVersion());
        assertFalse(credentialsRevoked());
    }

    @Test
    void genericPasswordUpdateDoesNotRequireSuperAdminButStillRevokesCredentials() {
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", "target"); details.put("username", "target-user"); details.put("globalRole", "USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("target", null, Collections.emptyList());
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
        service.updatePassword("target", PasswordUtil.hash("new-password"));
        assertTrue(PasswordUtil.verify("new-password", repository.findById("target").getPasswordHash()));
        assertEquals(1, repository.findById("target").getAuthVersion());
        assertTrue(credentialsRevoked()); assertFalse(repository.findById("target").isFirstLogin());
    }

    private boolean credentialsRevoked() {
        String web = jdbcTemplate.queryForObject("SELECT revoked_at FROM auth_session WHERE id='session'", String.class);
        String mcp = jdbcTemplate.queryForObject("SELECT revoked_at FROM mcp_token WHERE id='mcp'", String.class);
        assertEquals(web == null, mcp == null, "web and MCP credentials must change atomically");
        return web != null;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({UserServiceImpl.class, CredentialRevocationService.class, AuthSessionRepository.class, McpTokenRepository.class,
            AdminSafetyService.class, com.mockhub.system.repository.AdminSafetyRepository.class,
            com.mockhub.system.repository.TeamRepository.class})
    static class TransactionConfig {

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
