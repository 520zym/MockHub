package com.mockhub.auth.repository;

import com.mockhub.auth.model.AuthSession;
import com.mockhub.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AuthSessionRepositoryTest {
    @TempDir Path tempDir;
    private JdbcTemplate jdbc;
    private AuthSessionRepository sessions;
    private UserRepository users;
    private TransactionTemplate transaction;

    @BeforeEach void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("sessions.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) "
                + "VALUES('u','user','hash','User','USER','before','before'),('other','other','hash','Other','USER','before','before')");
        sessions = new AuthSessionRepository(jdbc);
        users = new UserRepository();
        ReflectionTestUtils.setField(users, "jdbcTemplate", jdbc);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    @Test void insertRequiresCurrentActiveAccountVersion() {
        assertTrue(sessions.insertIfCurrent(session("one", "u", 0)));
        users.incrementAuthVersion("u", "now");
        assertFalse(sessions.insertIfCurrent(session("stale", "u", 0)));
        assertFalse(sessions.isActive("one", "u", 0, 100));
        assertTrue(sessions.insertIfCurrent(session("two", "u", 1)));
        users.updateStatus("u", "DISABLED", "now");
        assertFalse(sessions.insertIfCurrent(session("disabled", "u", 1)));
        assertFalse(sessions.isActive("two", "u", 1, 100));
        assertFalse(sessions.insertIfCurrent(session("missing", "missing", 0)));
    }

    @Test void revokeIsOwnedAndOnlyAffectsChosenDevice() {
        sessions.insertIfCurrent(session("one", "u", 0));
        sessions.insertIfCurrent(session("two", "u", 0));
        sessions.insertIfCurrent(session("other", "other", 0));
        assertEquals(0, sessions.revoke("other", "u", "now"));
        assertEquals(1, sessions.revoke("one", "u", "now"));
        assertFalse(sessions.isActive("one", "u", 0, 100));
        assertTrue(sessions.isActive("two", "u", 0, 100));
        assertTrue(sessions.isActive("other", "other", 0, 100));
        assertEquals(1, sessions.findActiveByUser("u", 100).size());
        assertEquals(1, sessions.revokeAllByUser("u", "later"));
        assertTrue(sessions.findActiveByUser("u", 100).isEmpty());
    }

    @Test void expiredSessionsAreNeverAuthenticatedOrListed() {
        sessions.insertIfCurrent(session("expired", "u", 0));
        assertFalse(sessions.isActive("expired", "u", 0, 1000));
        assertTrue(sessions.findActiveByUser("u", 1000).isEmpty());
    }

    @Test void versionAndSessionWritesParticipateInRealSqliteRollback() {
        sessions.insertIfCurrent(session("one", "u", 0));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            users.incrementAuthVersion("u", "now");
            sessions.revokeAllByUser("u", "now");
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, users.findById("u").getAuthVersion());
        assertTrue(sessions.isActive("one", "u", 0, 100));
    }

    private AuthSession session(String id, String userId, long version) {
        AuthSession session = new AuthSession();
        session.setId(id); session.setUserId(userId); session.setAuthVersion(version);
        session.setCreatedAt("now"); session.setExpiresAt(1000);
        return session;
    }
}
