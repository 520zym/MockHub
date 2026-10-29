package com.mockhub.system.service;

import com.mockhub.common.util.PasswordUtil;
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
        jdbcTemplate.execute("CREATE TABLE user (id TEXT PRIMARY KEY, username TEXT, password_hash TEXT, "
                + "display_name TEXT, global_role TEXT, first_login INTEGER, created_at TEXT, updated_at TEXT)");
        oldHash = PasswordUtil.hash("old-password");
        jdbcTemplate.update("INSERT INTO user VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "target", "target-user", oldHash, "Target", "USER", 0, "before", "before");
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
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import(UserServiceImpl.class)
    static class TransactionConfig {

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
