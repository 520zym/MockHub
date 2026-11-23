package com.mockhub.common.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * Frozen historical schema.sql statements, obtained with git show <commit>:src/main/resources/schema.sql.
 * Comments/whitespace alone are removed; no current schema or current migration is used to create old fixtures.
 * Version numbers are the actual executeMigrations dispatcher maxima at those commits, not arbitrary labels.
 * The pre-vendor AI snapshot is 83f62b3^ (d463a6f), whose dispatcher ends at V5.
 */
final class LegacySchemaFixtures {
    static final class Snapshot {
        final String name;
        final int version;
        final String commit;
        private final int[] statements;
        Snapshot(String name, int version, String commit, int[] statements) {
            this.name = name; this.version = version; this.commit = commit; this.statements = statements;
        }
        void create(Connection conn) throws Exception {
            try (Statement st = conn.createStatement()) {
                for (int index : statements) st.execute(SQL[index]);
                // Historical migrateV3 creates this index even when schema.sql no longer declares it.
                if (version >= 3) st.execute("CREATE INDEX IF NOT EXISTS idx_api_definition_last_called_at ON api_definition(last_called_at)");
            }
            if (version > 0) try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO schema_version(version,description) VALUES(?,?)")) {
                for (int v = 1; v <= version; v++) {
                    ps.setInt(1, v); ps.setString(2, "Historical " + commit + " V" + v); ps.executeUpdate();
                }
            }
        }
        @Override public String toString() { return name + "@" + commit; }
    }

    static final List<Snapshot> ALL = Arrays.asList(
        new Snapshot("unversioned-initial", 0, "3b6467d", new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26}),
        new Snapshot("unversioned-responses", 0, "97699fb", new int[]{0,1,2,3,4,5,6,7,8,27,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,28,29,30}),
        new Snapshot("v1", 1, "674abdb", new int[]{0,1,2,3,4,5,6,7,8,27,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42}),
        new Snapshot("v2", 2, "b407571", new int[]{0,1,2,3,4,5,6,7,8,27,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42}),
        new Snapshot("v3", 3, "f57e2a5", new int[]{0,1,2,3,4,5,6,7,8,43,10,11,12,13,44,14,15,16,17,18,19,20,21,22,23,24,25,26,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42}),
        new Snapshot("v4", 4, "0903899", new int[]{0,1,2,3,4,5,6,7,8,43,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42}),
        new Snapshot("v5", 5, "38a836a", new int[]{0,1,2,3,4,5,6,7,8,46,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50}),
        new Snapshot("v5-ai-pre-vendor", 5, "d463a6f", new int[]{0,1,2,3,4,5,6,7,8,46,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,51,52,53,54,55,56,57,58,59,60}),
        new Snapshot("v6", 6, "83f62b3", new int[]{0,1,2,3,4,5,6,7,8,46,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,53,54,55,56,57,58,59,60}),
        new Snapshot("v7", 7, "27de6be", new int[]{0,1,2,3,4,5,6,7,8,46,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,53,54,55,62,57,58,59,60,63,64,65,66}),
        new Snapshot("v7-compatibility", 7, "73397b7", new int[]{0,1,2,3,4,5,6,7,8,46,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,53,54,55,62,57,58,59,60,63,64,65,66}),
        new Snapshot("v8", 8, "9805a23", new int[]{67,1,68,69,2,3,4,5,6,7,8,46,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,53,54,55,62,57,58,59,60,63,64,65,66}),
        new Snapshot("v9", 9, "8ed424b", new int[]{70,1,68,69,71,3,4,72,73,74,6,7,8,46,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,53,54,55,62,57,58,59,60,63,64,65,66}),
        new Snapshot("v10-initial", 10, "747705d", new int[]{70,1,68,69,71,3,4,72,73,74,6,7,8,75,76,11,12,13,77,78,79,80,81,82,83,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,53,54,55,62,57,58,59,60,63,64,65,66}),
        new Snapshot("v10-trash", 10, "24e638d", new int[]{70,1,68,69,71,3,4,72,73,74,6,7,8,75,76,11,12,13,77,84,78,85,80,81,82,83,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,53,54,55,62,57,58,59,60,63,64,65,66}),
        new Snapshot("v10-drafts", 10, "f9f9727", new int[]{70,1,68,69,71,3,4,72,73,74,6,7,8,75,76,11,12,13,77,84,78,85,80,81,82,83,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,86,54,55,62,57,58,59,60,63,64,65,66}),
        new Snapshot("v11", 11, "453d971", new int[]{70,1,68,69,71,3,4,72,73,74,6,7,8,75,76,11,12,13,77,84,78,85,80,81,82,83,14,15,16,17,18,19,20,21,22,23,24,25,26,45,29,30,31,32,33,34,35,36,37,38,39,40,41,42,47,48,49,50,61,52,87,54,55,62,57,88,59,60,63,64,65,66})
    );

    static Snapshot named(String name) {
        return ALL.stream().filter(s -> name.equals(s.name)).findFirst().orElseThrow(IllegalArgumentException::new);
    }

    // Each immutable SQL statement is shared only when byte-identical after whitespace normalization.
    private static final String[] SQL = {
        /* 0 */ "CREATE TABLE IF NOT EXISTS user ( id TEXT PRIMARY KEY, username TEXT NOT NULL, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, global_role TEXT NOT NULL DEFAULT 'MEMBER', first_login INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL )",
        /* 1 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username ON user(username)",
        /* 2 */ "CREATE TABLE IF NOT EXISTS team ( id TEXT PRIMARY KEY, name TEXT NOT NULL, identifier TEXT NOT NULL, color TEXT NOT NULL DEFAULT '#185FA5', created_at TEXT NOT NULL )",
        /* 3 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_team_identifier ON team(identifier)",
        /* 4 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_team_name ON team(name)",
        /* 5 */ "CREATE TABLE IF NOT EXISTS user_team ( user_id TEXT NOT NULL, team_id TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'MEMBER', PRIMARY KEY (user_id, team_id), FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE )",
        /* 6 */ "CREATE INDEX IF NOT EXISTS idx_user_team_team_id ON user_team(team_id)",
        /* 7 */ "CREATE TABLE IF NOT EXISTS api_group ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, name TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE )",
        /* 8 */ "CREATE INDEX IF NOT EXISTS idx_api_group_team_id ON api_group(team_id)",
        /* 9 */ "CREATE TABLE IF NOT EXISTS api_definition ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, group_id TEXT, type TEXT NOT NULL DEFAULT 'REST', name TEXT NOT NULL, method TEXT NOT NULL, path TEXT NOT NULL, response_code INTEGER NOT NULL DEFAULT 200, content_type TEXT NOT NULL DEFAULT 'application/json', response_body TEXT, delay_ms INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, global_header_overrides TEXT, soap_config TEXT, scenarios TEXT, created_by TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, updated_by TEXT, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE, FOREIGN KEY (group_id) REFERENCES api_group(id) ON DELETE SET NULL, FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL, FOREIGN KEY (updated_by) REFERENCES user(id) ON DELETE SET NULL )",
        /* 10 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_api_definition_team_path_method ON api_definition(team_id, path, method)",
        /* 11 */ "CREATE INDEX IF NOT EXISTS idx_api_definition_team_id ON api_definition(team_id)",
        /* 12 */ "CREATE INDEX IF NOT EXISTS idx_api_definition_group_id ON api_definition(group_id)",
        /* 13 */ "CREATE INDEX IF NOT EXISTS idx_api_definition_enabled ON api_definition(team_id, enabled)",
        /* 14 */ "CREATE TABLE IF NOT EXISTS tag ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, name TEXT NOT NULL, color TEXT NOT NULL DEFAULT '#3B6D11', FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE )",
        /* 15 */ "CREATE INDEX IF NOT EXISTS idx_tag_team_id ON tag(team_id)",
        /* 16 */ "CREATE TABLE IF NOT EXISTS api_tag ( api_id TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY (api_id, tag_id), FOREIGN KEY (api_id) REFERENCES api_definition(id) ON DELETE CASCADE, FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE )",
        /* 17 */ "CREATE INDEX IF NOT EXISTS idx_api_tag_tag_id ON api_tag(tag_id)",
        /* 18 */ "CREATE TABLE IF NOT EXISTS global_header ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, header_name TEXT NOT NULL, header_value TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, sort_order INTEGER NOT NULL DEFAULT 0, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE )",
        /* 19 */ "CREATE INDEX IF NOT EXISTS idx_global_header_team_id ON global_header(team_id)",
        /* 20 */ "CREATE TABLE IF NOT EXISTS operation_log ( id TEXT PRIMARY KEY, team_id TEXT, user_id TEXT, username TEXT NOT NULL, action TEXT NOT NULL, target_type TEXT NOT NULL, target_id TEXT, target_name TEXT, detail TEXT, created_at TEXT NOT NULL )",
        /* 21 */ "CREATE INDEX IF NOT EXISTS idx_operation_log_team_id ON operation_log(team_id)",
        /* 22 */ "CREATE INDEX IF NOT EXISTS idx_operation_log_created_at ON operation_log(created_at)",
        /* 23 */ "CREATE TABLE IF NOT EXISTS request_log ( id TEXT PRIMARY KEY, team_id TEXT, api_id TEXT, api_path TEXT, method TEXT, request_headers TEXT, request_body TEXT, request_params TEXT, response_code INTEGER, duration_ms INTEGER, created_at TEXT NOT NULL )",
        /* 24 */ "CREATE INDEX IF NOT EXISTS idx_request_log_team_id ON request_log(team_id)",
        /* 25 */ "CREATE INDEX IF NOT EXISTS idx_request_log_created_at ON request_log(created_at)",
        /* 26 */ "CREATE INDEX IF NOT EXISTS idx_request_log_api_id ON request_log(api_id)",
        /* 27 */ "CREATE TABLE IF NOT EXISTS api_definition ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, group_id TEXT, type TEXT NOT NULL DEFAULT 'REST', name TEXT NOT NULL, description TEXT, method TEXT NOT NULL, path TEXT NOT NULL, response_code INTEGER NOT NULL DEFAULT 200, content_type TEXT NOT NULL DEFAULT 'application/json', response_body TEXT, delay_ms INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, global_header_overrides TEXT, soap_config TEXT, scenarios TEXT, created_by TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, updated_by TEXT, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE, FOREIGN KEY (group_id) REFERENCES api_group(id) ON DELETE SET NULL, FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL, FOREIGN KEY (updated_by) REFERENCES user(id) ON DELETE SET NULL )",
        /* 28 */ "CREATE TABLE IF NOT EXISTS api_response ( id TEXT PRIMARY KEY, api_id TEXT NOT NULL, soap_operation_name TEXT, name TEXT NOT NULL DEFAULT 'Default', response_code INTEGER NOT NULL DEFAULT 200, content_type TEXT NOT NULL DEFAULT 'application/json', response_body TEXT, delay_ms INTEGER NOT NULL DEFAULT 0, is_active INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0, conditions TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, FOREIGN KEY (api_id) REFERENCES api_definition(id) ON DELETE CASCADE )",
        /* 29 */ "CREATE INDEX IF NOT EXISTS idx_api_response_api_id ON api_response(api_id)",
        /* 30 */ "CREATE INDEX IF NOT EXISTS idx_api_response_active ON api_response(api_id, is_active)",
        /* 31 */ "CREATE TABLE IF NOT EXISTS custom_variable ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, name TEXT NOT NULL, description TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE )",
        /* 32 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_variable_team_name ON custom_variable(team_id, name)",
        /* 33 */ "CREATE INDEX IF NOT EXISTS idx_custom_variable_team_id ON custom_variable(team_id)",
        /* 34 */ "CREATE TABLE IF NOT EXISTS custom_variable_value ( id TEXT PRIMARY KEY, variable_id TEXT NOT NULL, value TEXT NOT NULL, description TEXT, sort_order INTEGER NOT NULL DEFAULT 0, FOREIGN KEY (variable_id) REFERENCES custom_variable(id) ON DELETE CASCADE )",
        /* 35 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_variable_value_unique ON custom_variable_value(variable_id, value)",
        /* 36 */ "CREATE INDEX IF NOT EXISTS idx_custom_variable_value_variable_id ON custom_variable_value(variable_id)",
        /* 37 */ "CREATE TABLE IF NOT EXISTS custom_variable_group ( id TEXT PRIMARY KEY, variable_id TEXT NOT NULL, name TEXT NOT NULL, description TEXT, FOREIGN KEY (variable_id) REFERENCES custom_variable(id) ON DELETE CASCADE )",
        /* 38 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_variable_group_unique ON custom_variable_group(variable_id, name)",
        /* 39 */ "CREATE INDEX IF NOT EXISTS idx_custom_variable_group_variable_id ON custom_variable_group(variable_id)",
        /* 40 */ "CREATE TABLE IF NOT EXISTS custom_variable_group_value ( group_id TEXT NOT NULL, value_id TEXT NOT NULL, PRIMARY KEY (group_id, value_id), FOREIGN KEY (group_id) REFERENCES custom_variable_group(id) ON DELETE CASCADE, FOREIGN KEY (value_id) REFERENCES custom_variable_value(id) ON DELETE CASCADE )",
        /* 41 */ "CREATE INDEX IF NOT EXISTS idx_custom_variable_group_value_value_id ON custom_variable_group_value(value_id)",
        /* 42 */ "CREATE TABLE IF NOT EXISTS schema_version ( version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT (datetime('now')), description TEXT )",
        /* 43 */ "CREATE TABLE IF NOT EXISTS api_definition ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, group_id TEXT, type TEXT NOT NULL DEFAULT 'REST', name TEXT NOT NULL, description TEXT, method TEXT NOT NULL, path TEXT NOT NULL, response_code INTEGER NOT NULL DEFAULT 200, content_type TEXT NOT NULL DEFAULT 'application/json', response_body TEXT, delay_ms INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, global_header_overrides TEXT, soap_config TEXT, scenarios TEXT, hit_count INTEGER NOT NULL DEFAULT 0, last_called_at TEXT, created_by TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, updated_by TEXT, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE, FOREIGN KEY (group_id) REFERENCES api_group(id) ON DELETE SET NULL, FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL, FOREIGN KEY (updated_by) REFERENCES user(id) ON DELETE SET NULL )",
        /* 44 */ "CREATE INDEX IF NOT EXISTS idx_api_definition_last_called_at ON api_definition(last_called_at)",
        /* 45 */ "CREATE TABLE IF NOT EXISTS api_response ( id TEXT PRIMARY KEY, api_id TEXT NOT NULL, soap_operation_name TEXT, name TEXT NOT NULL DEFAULT 'Default', response_code INTEGER NOT NULL DEFAULT 200, content_type TEXT NOT NULL DEFAULT 'application/json', response_body TEXT, body_type TEXT NOT NULL DEFAULT 'TEXT', file_name TEXT, file_path TEXT, download_name TEXT, file_size INTEGER, delay_ms INTEGER NOT NULL DEFAULT 0, is_active INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0, conditions TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, FOREIGN KEY (api_id) REFERENCES api_definition(id) ON DELETE CASCADE )",
        /* 46 */ "CREATE TABLE IF NOT EXISTS api_definition ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, group_id TEXT, type TEXT NOT NULL DEFAULT 'REST', name TEXT NOT NULL, description TEXT, method TEXT NOT NULL, path TEXT NOT NULL, response_code INTEGER NOT NULL DEFAULT 200, content_type TEXT NOT NULL DEFAULT 'application/json', response_body TEXT, delay_ms INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, global_header_overrides TEXT, soap_config TEXT, scenarios TEXT, response_mode TEXT NOT NULL DEFAULT 'CONDITION', hit_count INTEGER NOT NULL DEFAULT 0, last_called_at TEXT, created_by TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, updated_by TEXT, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE, FOREIGN KEY (group_id) REFERENCES api_group(id) ON DELETE SET NULL, FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL, FOREIGN KEY (updated_by) REFERENCES user(id) ON DELETE SET NULL )",
        /* 47 */ "CREATE TABLE IF NOT EXISTS file_server_file ( file_id TEXT PRIMARY KEY, team_id TEXT NOT NULL, file_name TEXT NOT NULL, alias TEXT NOT NULL DEFAULT '', content_type TEXT NOT NULL, file_size INTEGER NOT NULL, uploaded_at TEXT NOT NULL, download_count INTEGER NOT NULL DEFAULT 0, transferred_bytes INTEGER NOT NULL DEFAULT 0, last_downloaded_at TEXT, deleted INTEGER NOT NULL DEFAULT 0 )",
        /* 48 */ "CREATE INDEX IF NOT EXISTS idx_file_server_team_time ON file_server_file(team_id, uploaded_at)",
        /* 49 */ "CREATE TABLE IF NOT EXISTS file_server_tag ( file_id TEXT NOT NULL, tag TEXT NOT NULL, PRIMARY KEY (file_id, tag) )",
        /* 50 */ "CREATE INDEX IF NOT EXISTS idx_file_server_tag_name ON file_server_tag(tag)",
        /* 51 */ "CREATE TABLE IF NOT EXISTS ai_settings ( id TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 0, provider TEXT NOT NULL DEFAULT 'OPENAI_COMPATIBLE', base_url TEXT, model TEXT, api_key TEXT, cap_response_body INTEGER NOT NULL DEFAULT 1, cap_conditions INTEGER NOT NULL DEFAULT 1, cap_explain INTEGER NOT NULL DEFAULT 1, cap_drafts INTEGER NOT NULL DEFAULT 1, access_scope TEXT NOT NULL DEFAULT 'ALL_MEMBERS', updated_at TEXT, updated_by TEXT )",
        /* 52 */ "INSERT OR IGNORE INTO ai_settings (id, enabled) VALUES ('default', 0)",
        /* 53 */ "CREATE TABLE IF NOT EXISTS ai_pending_action ( id TEXT PRIMARY KEY, user_id TEXT NOT NULL, team_id TEXT, action_type TEXT NOT NULL, payload TEXT NOT NULL, summary TEXT, status TEXT NOT NULL DEFAULT 'PENDING', created_at TEXT NOT NULL, expires_at TEXT NOT NULL, confirmed_at TEXT, result_api_id TEXT )",
        /* 54 */ "CREATE INDEX IF NOT EXISTS idx_ai_pending_action_user ON ai_pending_action(user_id, status)",
        /* 55 */ "CREATE INDEX IF NOT EXISTS idx_ai_pending_action_expires ON ai_pending_action(status, expires_at)",
        /* 56 */ "CREATE TABLE IF NOT EXISTS mcp_settings ( id TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 0, write_mode TEXT NOT NULL DEFAULT 'DRAFT_ONLY', tool_search_apis INTEGER NOT NULL DEFAULT 1, tool_get_api_detail INTEGER NOT NULL DEFAULT 1, tool_get_api_stats INTEGER NOT NULL DEFAULT 1, tool_create_api_draft INTEGER NOT NULL DEFAULT 1, tool_apply_api_draft INTEGER NOT NULL DEFAULT 0, tool_generate_response_body INTEGER NOT NULL DEFAULT 1, tool_generate_conditions INTEGER NOT NULL DEFAULT 1, tool_explain_match_result INTEGER NOT NULL DEFAULT 1, updated_at TEXT, updated_by TEXT )",
        /* 57 */ "INSERT OR IGNORE INTO mcp_settings (id, enabled, write_mode) VALUES ('default', 0, 'DRAFT_ONLY')",
        /* 58 */ "CREATE TABLE IF NOT EXISTS mcp_token ( id TEXT PRIMARY KEY, name TEXT NOT NULL, token_hash TEXT NOT NULL, token_prefix TEXT NOT NULL, user_id TEXT NOT NULL, created_at TEXT NOT NULL, last_used_at TEXT, expires_at TEXT, revoked_at TEXT )",
        /* 59 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_mcp_token_hash ON mcp_token(token_hash)",
        /* 60 */ "CREATE INDEX IF NOT EXISTS idx_mcp_token_user ON mcp_token(user_id, revoked_at)",
        /* 61 */ "CREATE TABLE IF NOT EXISTS ai_settings ( id TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 0, provider TEXT NOT NULL DEFAULT 'OPENAI_COMPATIBLE', vendor TEXT NOT NULL DEFAULT 'CUSTOM', base_url TEXT, model TEXT, api_key TEXT, cap_response_body INTEGER NOT NULL DEFAULT 1, cap_conditions INTEGER NOT NULL DEFAULT 1, cap_explain INTEGER NOT NULL DEFAULT 1, cap_drafts INTEGER NOT NULL DEFAULT 1, access_scope TEXT NOT NULL DEFAULT 'ALL_MEMBERS', updated_at TEXT, updated_by TEXT )",
        /* 62 */ "CREATE TABLE IF NOT EXISTS mcp_settings ( id TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 0, write_mode TEXT NOT NULL DEFAULT 'DRAFT_ONLY', tool_search_apis INTEGER NOT NULL DEFAULT 1, tool_get_api_detail INTEGER NOT NULL DEFAULT 1, tool_get_api_stats INTEGER NOT NULL DEFAULT 1, tool_create_api_draft INTEGER NOT NULL DEFAULT 1, tool_update_api_draft INTEGER NOT NULL DEFAULT 1, tool_apply_api_draft INTEGER NOT NULL DEFAULT 0, tool_generate_response_body INTEGER NOT NULL DEFAULT 1, tool_generate_conditions INTEGER NOT NULL DEFAULT 1, tool_explain_match_result INTEGER NOT NULL DEFAULT 1, updated_at TEXT, updated_by TEXT )",
        /* 63 */ "CREATE TABLE IF NOT EXISTS ai_conversation ( id TEXT PRIMARY KEY, user_id TEXT NOT NULL, team_id TEXT, title TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL )",
        /* 64 */ "CREATE INDEX IF NOT EXISTS idx_ai_conversation_user ON ai_conversation(user_id, updated_at)",
        /* 65 */ "CREATE TABLE IF NOT EXISTS ai_conversation_message ( id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL, role TEXT NOT NULL, content TEXT, cards TEXT, provider TEXT, model TEXT, created_at TEXT NOT NULL )",
        /* 66 */ "CREATE INDEX IF NOT EXISTS idx_ai_conversation_message ON ai_conversation_message(conversation_id)",
        /* 67 */ "CREATE TABLE IF NOT EXISTS user ( id TEXT PRIMARY KEY, username TEXT NOT NULL, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, global_role TEXT NOT NULL DEFAULT 'MEMBER', first_login INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')), auth_version INTEGER NOT NULL DEFAULT 0 CHECK (auth_version >= 0), created_at TEXT NOT NULL, updated_at TEXT NOT NULL )",
        /* 68 */ "CREATE TABLE IF NOT EXISTS auth_session ( id TEXT PRIMARY KEY, user_id TEXT NOT NULL, auth_version INTEGER NOT NULL, created_at TEXT NOT NULL, expires_at INTEGER NOT NULL, revoked_at TEXT, user_agent TEXT, ip_address TEXT, FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE )",
        /* 69 */ "CREATE INDEX IF NOT EXISTS idx_auth_session_user ON auth_session(user_id)",
        /* 70 */ "CREATE TABLE IF NOT EXISTS user ( id TEXT PRIMARY KEY, username TEXT NOT NULL, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, global_role TEXT NOT NULL DEFAULT 'USER', first_login INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')), auth_version INTEGER NOT NULL DEFAULT 0 CHECK (auth_version >= 0), created_at TEXT NOT NULL, updated_at TEXT NOT NULL )",
        /* 71 */ "CREATE TABLE IF NOT EXISTS team ( id TEXT PRIMARY KEY, name TEXT NOT NULL, identifier TEXT NOT NULL, color TEXT NOT NULL DEFAULT '#185FA5', description TEXT, status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'ARCHIVED')), created_by TEXT, created_at TEXT NOT NULL )",
        /* 72 */ "CREATE TABLE IF NOT EXISTS security_guard ( id INTEGER PRIMARY KEY CHECK(id = 1), revision INTEGER NOT NULL DEFAULT 0 )",
        /* 73 */ "INSERT OR IGNORE INTO security_guard(id, revision) VALUES(1, 0)",
        /* 74 */ "CREATE TABLE IF NOT EXISTS user_team ( user_id TEXT NOT NULL, team_id TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'EDITOR', PRIMARY KEY (user_id, team_id), FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE )",
        /* 75 */ "CREATE TABLE IF NOT EXISTS api_definition ( id TEXT PRIMARY KEY, team_id TEXT NOT NULL, group_id TEXT, type TEXT NOT NULL DEFAULT 'REST', name TEXT NOT NULL, description TEXT, method TEXT NOT NULL, path TEXT NOT NULL, response_code INTEGER NOT NULL DEFAULT 200, content_type TEXT NOT NULL DEFAULT 'application/json', response_body TEXT, delay_ms INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, global_header_overrides TEXT, soap_config TEXT, scenarios TEXT, response_mode TEXT NOT NULL DEFAULT 'CONDITION', hit_count INTEGER NOT NULL DEFAULT 0, last_called_at TEXT, created_by TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, updated_by TEXT, revision INTEGER NOT NULL DEFAULT 1 CHECK(revision >= 1), deleted_at TEXT, deleted_by TEXT, purge_after TEXT, FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE, FOREIGN KEY (group_id) REFERENCES api_group(id) ON DELETE SET NULL, FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL, FOREIGN KEY (updated_by) REFERENCES user(id) ON DELETE SET NULL )",
        /* 76 */ "CREATE UNIQUE INDEX IF NOT EXISTS idx_api_definition_team_path_method ON api_definition(team_id, path, method) WHERE deleted_at IS NULL",
        /* 77 */ "CREATE TABLE IF NOT EXISTS api_revision ( id TEXT PRIMARY KEY, api_id TEXT NOT NULL, team_id TEXT NOT NULL, revision INTEGER NOT NULL, action TEXT NOT NULL, actor_id TEXT, actor_name_snapshot TEXT NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL, snapshot_json TEXT NOT NULL, snapshot_schema_version INTEGER NOT NULL DEFAULT 1, restored_from_revision_id TEXT, UNIQUE(api_id, revision) )",
        /* 78 */ "CREATE INDEX IF NOT EXISTS idx_api_revision_api ON api_revision(api_id, revision DESC)",
        /* 79 */ "CREATE TABLE IF NOT EXISTS api_lifecycle_lock (id INTEGER PRIMARY KEY CHECK(id=1), marker INTEGER NOT NULL DEFAULT 0)",
        /* 80 */ "INSERT OR IGNORE INTO api_lifecycle_lock(id,marker) VALUES(1,0)",
        /* 81 */ "CREATE TABLE IF NOT EXISTS mock_blob (file_path TEXT PRIMARY KEY, team_id TEXT NOT NULL, lease_expires_at INTEGER NOT NULL DEFAULT 0)",
        /* 82 */ "CREATE TABLE IF NOT EXISTS mock_blob_ref (file_path TEXT NOT NULL, owner_type TEXT NOT NULL, owner_id TEXT NOT NULL, expires_at INTEGER, PRIMARY KEY(file_path,owner_type,owner_id))",
        /* 83 */ "CREATE INDEX IF NOT EXISTS idx_mock_blob_ref_owner ON mock_blob_ref(owner_type,owner_id)",
        /* 84 */ "CREATE TABLE IF NOT EXISTS api_tombstone (api_id TEXT PRIMARY KEY, team_id TEXT NOT NULL, name TEXT, method TEXT, path TEXT, revision INTEGER NOT NULL, deleted_at TEXT, deleted_by TEXT, purged_at TEXT NOT NULL, purged_by TEXT)",
        /* 85 */ "CREATE TABLE IF NOT EXISTS api_lifecycle_lock (id INTEGER PRIMARY KEY CHECK(id=1), marker INTEGER NOT NULL DEFAULT 0, history_delete_allowed INTEGER NOT NULL DEFAULT 0)",
        /* 86 */ "CREATE TABLE IF NOT EXISTS ai_pending_action ( id TEXT PRIMARY KEY, user_id TEXT NOT NULL, team_id TEXT, action_type TEXT NOT NULL, payload TEXT NOT NULL, summary TEXT, status TEXT NOT NULL DEFAULT 'PENDING', created_at TEXT NOT NULL, expires_at TEXT NOT NULL, confirmed_at TEXT, result_api_id TEXT, base_revision INTEGER, target_api_id TEXT )",
        /* 87 */ "CREATE TABLE IF NOT EXISTS ai_pending_action ( id TEXT PRIMARY KEY, user_id TEXT NOT NULL, team_id TEXT, action_type TEXT NOT NULL, payload TEXT NOT NULL, summary TEXT, status TEXT NOT NULL DEFAULT 'PENDING', created_at TEXT NOT NULL, expires_at TEXT NOT NULL, confirmed_at TEXT, result_api_id TEXT, base_revision INTEGER, target_api_id TEXT, source_type TEXT NOT NULL DEFAULT 'WEB', source_token_id TEXT, source_scope_json TEXT )",
        /* 88 */ "CREATE TABLE IF NOT EXISTS mcp_token ( id TEXT PRIMARY KEY, name TEXT NOT NULL, token_hash TEXT NOT NULL, token_prefix TEXT NOT NULL, scope_version INTEGER NOT NULL DEFAULT 0, team_ids TEXT, scopes TEXT, user_id TEXT NOT NULL, created_at TEXT NOT NULL, last_used_at TEXT, expires_at TEXT, revoked_at TEXT )"
    };
    private LegacySchemaFixtures() { }
}
