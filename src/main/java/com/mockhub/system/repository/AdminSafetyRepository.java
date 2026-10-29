package com.mockhub.system.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;

/** All safety queries use the caller's transaction and the same SQLite connection. */
@Repository
public class AdminSafetyRepository {
    @Autowired private JdbcTemplate jdbcTemplate;

    public void acquireWriteLock() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(jdbcTemplate.getDataSource())) {
            throw new IllegalStateException("Administrator protection requires a datasource transaction");
        }
        // MUST be the first database statement: a deferred read before this can race or fail upgrading.
        if (jdbcTemplate.update("UPDATE security_guard SET revision=revision WHERE id=1") != 1) {
            throw new IllegalStateException("Missing security_guard row");
        }
    }

    public int countEffectiveTeamAdmins(String teamId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM user_team ut JOIN user u ON u.id=ut.user_id "
                + "WHERE ut.team_id=? AND ut.role='TEAM_ADMIN' AND u.status='ACTIVE'", Integer.class, teamId);
    }

    public int countEffectiveSuperAdmins() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM user WHERE global_role='SUPER_ADMIN' AND status='ACTIVE'", Integer.class);
    }

    public List<String> findEffectiveAdminTeamIds(String userId) {
        return jdbcTemplate.queryForList("SELECT ut.team_id FROM user_team ut JOIN user u ON u.id=ut.user_id "
                + "WHERE ut.user_id=? AND ut.role='TEAM_ADMIN' AND u.status='ACTIVE'", String.class, userId);
    }

    /** Explicit cleanup: correctness must not depend on SQLite's foreign_keys connection default. */
    public void detachDeletedUser(String userId) {
        jdbcTemplate.update("DELETE FROM user_team WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM auth_session WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM mcp_token WHERE user_id=?", userId);
        jdbcTemplate.update("UPDATE api_definition SET created_by=NULL WHERE created_by=?", userId);
        jdbcTemplate.update("UPDATE api_definition SET updated_by=NULL WHERE updated_by=?", userId);
        jdbcTemplate.update("UPDATE team SET created_by=NULL WHERE created_by=?", userId);
        jdbcTemplate.update("UPDATE ai_settings SET updated_by=NULL WHERE updated_by=?", userId);
        jdbcTemplate.update("UPDATE mcp_settings SET updated_by=NULL WHERE updated_by=?", userId);
        // Preserve AI history and pending-action audit identifiers; these are not FK references.
    }
}
