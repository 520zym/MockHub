package com.mockhub.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.stream.Stream;

import static com.mockhub.common.config.LegacyDatabaseCompatibilityTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Failure-path regression: actual historical SQLite DDL, never a current schema with an invented old version. */
class SchemaVersionMigrationTest {
    @TempDir Path temp;

    static Stream<LegacySchemaFixtures.Snapshot> histories() { return LegacySchemaFixtures.ALL.stream(); }

    @ParameterizedTest(name = "invalid roles: {0}")
    @MethodSource("histories")
    void illegalGlobalAndTeamRolesAbortEveryKnownVersionWithoutChangingAnything(LegacySchemaFixtures.Snapshot history) throws Exception {
        SQLiteDataSource ds = database(history);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("UPDATE user SET global_role='BROKEN_GLOBAL' WHERE id='legacy'");
        jdbc.update("UPDATE user_team SET role='BROKEN_ADMIN' WHERE user_id='legacy'");
        Path resource = temp.resolve("retained-resource.bin"); Files.write(resource, new byte[]{1, 2, 3});
        List<String> before = dump(ds);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> start(temp));
        String report = failure.getMessage();
        assertTrue(report.contains("user[id='legacy'].global_role='BROKEN_GLOBAL'"), report);
        assertTrue(report.contains("user_team[user_id='legacy',team_id='old-team'].role='BROKEN_ADMIN'"), report);
        assertTrue(report.contains("人工修复"));
        assertFalse(report.contains("password-hash-secret"));
        assertFalse(report.contains("api-key-secret"));
        assertEquals(before, dump(ds), "schema, schema_version, every row and every secret must remain unchanged");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(resource));
        assertThrows(IllegalStateException.class, () -> migrate(ds));
        assertEquals(before, dump(ds), "a repeated invalid startup must fail identically without partial changes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"v7", "v11"})
    void nullEmptyWhitespaceAndUnsupportedRolesFailClosedWithExactIdentities(String version) throws Exception {
        SQLiteDataSource ds = database(LegacySchemaFixtures.named(version));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // Deliberately corrupt fixtures only: permit NULL to model hand-edited/externally imported legacy databases.
        // This is not claimed to be a historical release schema and production must never auto-repair it.
        jdbc.execute("CREATE TABLE user_copy AS SELECT * FROM user"); jdbc.execute("DROP TABLE user"); jdbc.execute("ALTER TABLE user_copy RENAME TO user");
        jdbc.execute("CREATE TABLE relation_copy AS SELECT * FROM user_team"); jdbc.execute("DROP TABLE user_team"); jdbc.execute("ALTER TABLE relation_copy RENAME TO user_team");
        for (String role : new String[]{null, "", " ", "ADMIN", "editor", "EDITOR\n"}) {
            jdbc.update("UPDATE user SET global_role=?", new Object[]{role}, new int[]{java.sql.Types.VARCHAR});
            jdbc.update("UPDATE user_team SET role=?", new Object[]{role}, new int[]{java.sql.Types.VARCHAR});
            List<String> before = dump(ds);
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> migrate(ds));
            String expected = role == null ? "NULL" : "'" + role.replace("\n", "\\n") + "'";
            assertTrue(failure.getMessage().contains("global_role=" + expected), failure.getMessage());
            assertTrue(failure.getMessage().contains(".role=" + expected), failure.getMessage());
            assertEquals(before, dump(ds));
        }
    }

    @Test void documentedAliasesAndCurrentRolesAreAcceptedEvenAtV11() throws Exception {
        SQLiteDataSource ds = database(LegacySchemaFixtures.named("v11"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        for (String global : new String[]{"MEMBER", "TEAM_ADMIN", "USER", "SUPER_ADMIN"}) {
            for (String team : new String[]{"MEMBER", "TEAM_ADMIN", "EDITOR", "VIEWER"}) {
                jdbc.update("UPDATE user SET global_role=?", global);
                jdbc.update("UPDATE user_team SET role=?", team);
                migrate(ds); migrate(ds);
                assertEquals(global.equals("SUPER_ADMIN") ? "SUPER_ADMIN" : "USER", jdbc.queryForObject("SELECT global_role FROM user", String.class));
                assertEquals(team.equals("MEMBER") ? "EDITOR" : team, jdbc.queryForObject("SELECT role FROM user_team", String.class));
                assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_team", Integer.class));
            }
        }
    }

    @Test void schemaCreationFailureRollsBackUnversionedDatabaseCompletely() throws Exception {
        SQLiteDataSource ds = database(LegacySchemaFixtures.named("unversioned-initial"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // Reject incompatible pre-existing objects instead of silently skipping every failed index.
        jdbc.execute("CREATE VIEW api_revision AS SELECT id FROM user");
        List<String> before = dump(ds);
        assertThrows(IllegalStateException.class, () -> migrate(ds));
        assertEquals(before, dump(ds));
        assertFalse(hasTable(ds, "schema_version"));
        assertFalse(hasColumn(ds, "user", "status"));
        assertEquals("MEMBER", jdbc.queryForObject("SELECT global_role FROM user", String.class));
        assertEquals("MEMBER", jdbc.queryForObject("SELECT role FROM user_team", String.class));
    }

    @Test void finalVersionWriteFailureRollsBackAllPriorDdlDataAndRoleConversions() throws Exception {
        SQLiteDataSource ds = database(LegacySchemaFixtures.named("v1"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TRIGGER fail_last_version BEFORE INSERT ON schema_version WHEN NEW.version=11 "
                + "BEGIN SELECT RAISE(ABORT,'fixture late failure'); END");
        List<String> before = dump(ds);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> start(temp));
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        assertTrue(cause.getMessage().contains("fixture late failure"), cause.getMessage());
        assertEquals(before, dump(ds));
        assertFalse(hasColumn(ds, "user", "status"));
        assertFalse(hasColumn(ds, "api_definition", "revision"));
        assertEquals(1, jdbc.queryForObject("SELECT max(version) FROM schema_version", Integer.class));
        assertEquals("MEMBER", jdbc.queryForObject("SELECT global_role FROM user", String.class));
        assertEquals("MEMBER", jdbc.queryForObject("SELECT role FROM user_team", String.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {12L, 2147483648L, Long.MAX_VALUE})
    void unknownFutureVersionIsRejectedWithoutDowngradeSchemaOrJournalChanges(long version) throws Exception {
        SQLiteDataSource ds = database(LegacySchemaFixtures.named("v11"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO schema_version(version,description) VALUES(?,'future-unknown')", version);
        List<String> before = dump(ds);
        byte[] bytes = Files.readAllBytes(temp.resolve("legacy.db"));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> start(temp));
        assertTrue(failure.getMessage().contains("schema v" + version), failure.getMessage());
        assertTrue(failure.getMessage().contains("禁止降级"));
        assertEquals(before, dump(ds));
        assertArrayEquals(bytes, Files.readAllBytes(temp.resolve("legacy.db")));
    }

    @Test void missingRoleColumnCannotSilentlyGrantDefaultEditableAccess() throws Exception {
        SQLiteDataSource ds = database(LegacySchemaFixtures.named("v7"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE relation_copy AS SELECT user_id,team_id FROM user_team");
        jdbc.execute("DROP TABLE user_team"); jdbc.execute("ALTER TABLE relation_copy RENAME TO user_team");
        List<String> before = dump(ds);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> migrate(ds));
        assertTrue(failure.getMessage().contains("user_team.role 缺列"));
        assertEquals(before, dump(ds));
    }

    private SQLiteDataSource database(LegacySchemaFixtures.Snapshot history) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource(); ds.setUrl("jdbc:sqlite:" + temp.resolve("legacy.db"));
        try (Connection c = ds.getConnection()) { history.create(c); }
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES('legacy','Old user','password-hash-secret','Old','MEMBER','created','updated')");
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('old-team','Old team','OLD','created')");
        jdbc.update("INSERT INTO user_team(user_id,team_id,role) VALUES('legacy','old-team','MEMBER')");
        if (hasTable(ds, "ai_settings")) jdbc.update("UPDATE ai_settings SET api_key='api-key-secret' WHERE id='default'");
        return ds;
    }
}
