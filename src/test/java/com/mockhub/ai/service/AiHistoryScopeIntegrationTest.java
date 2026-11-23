package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.controller.AiController;
import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.model.AiConversationMessage;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.repository.AiConversationRepository;
import com.mockhub.ai.repository.AiPendingActionRepository;
import com.mockhub.common.filter.JwtAuthFilter;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.CurrentUser;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.common.util.CurrentUserProvider;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.service.ApiService;
import com.mockhub.stats.service.StatsService;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.PermissionCheckerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SQLite, real permission checker, real history/pending services and JWT HTTP filter. No application server. */
class AiHistoryScopeIntegrationTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate jdbc;
    private AiConversationService history;
    private PendingActionService pending;
    private AiSettingsService settings;
    private OpenAiCompatibleProvider provider;
    private ApiService apis;
    private MockMvc mvc;
    private JwtUtil jwt;

    @BeforeEach void setup() {
        SQLiteDataSource data = new SQLiteDataSource(); data.setUrl("jdbc:sqlite:" + temp.resolve("private-ai.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(data); jdbc = new JdbcTemplate(data);
        for (String id : Arrays.asList("u", "other", "root")) jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?,'hash',?,?,'before','before')", id, id, id, "root".equals(id) ? "SUPER_ADMIN" : "USER");
        for (String id : Arrays.asList("a", "b")) jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES(?,?,?,'before')", id, id, id);
        jdbc.update("INSERT INTO user_team VALUES('u','a','VIEWER'),('u','b','EDITOR'),('other','a','EDITOR')");
        UserRepository users = new UserRepository(); ReflectionTestUtils.setField(users, "jdbcTemplate", jdbc);
        TeamRepository teams = new TeamRepository(); ReflectionTestUtils.setField(teams, "jdbcTemplate", jdbc);
        PermissionCheckerImpl permissions = new PermissionCheckerImpl(); ReflectionTestUtils.setField(permissions, "userRepository", users); ReflectionTestUtils.setField(permissions, "teamRepository", teams);
        AiConversationRepository repository = new AiConversationRepository(jdbc);
        history = new AiConversationService(repository, json, permissions, teams);
        for (String[] row : new String[][]{{"ca","u","a"},{"cb","u","b"},{"foreign","other","a"},{"legacy","u",null},{"root-legacy","root",null},{"orphan","u","missing"},{"root-own","root","a"}}) seedConversation(repository, row[0], row[1], row[2]);
        apis = mock(ApiService.class); pending = new PendingActionService(new AiPendingActionRepository(jdbc), json, apis, permissions, mock(LogService.class));
        seedDraft("draft-a", "u", "a"); seedDraft("foreign-draft", "other", "a"); seedDraft("legacy-draft", "u", null); seedDraft("root-legacy-draft", "root", null); seedDraft("orphan-draft", "root", "missing");
        settings = mock(AiSettingsService.class); provider = mock(OpenAiCompatibleProvider.class);
        AiSettings config = new AiSettings(); config.setEnabled(true); config.setProvider(AiSettings.PROVIDER_MOCK); config.setCapDrafts(true);
        when(settings.assertUsable(any(), any())).thenReturn(config);
        AiChatService chat = new AiChatService(settings, provider, mock(MockAiGenerator.class), mock(AiService.class), apis, mock(StatsService.class), pending, history, json);
        AiController controller = new AiController(settings, mock(AiModelCatalogService.class), mock(AiService.class), mock(AiExplainService.class), chat, history, pending, mock(LogService.class));
        jwt = new JwtUtil(); jwt.init(); CurrentUserProvider current = mock(CurrentUserProvider.class);
        when(current.findBySession(anyString(), eq(0L), anyString())).thenAnswer(call -> { String user = call.getArgument(0); String role = jdbc.queryForObject("SELECT global_role FROM user WHERE id=?", String.class, user); return new CurrentUser(user, user, role, jdbc.queryForList("SELECT team_id FROM user_team WHERE user_id=?", String.class, user)); });
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).addFilters(new JwtAuthFilter(jwt, json, current)).build();
    }

    @Test void noTeamListReturnsOnlyOwnersCurrentlyReadableBoundTeams() throws Exception {
        JsonNode list = getData("u", "/api/ai/conversations"); assertEquals(2, list.size());
        String serialized = list.toString(); assertTrue(serialized.contains("secret-ca")); assertTrue(serialized.contains("secret-cb"));
        assertFalse(serialized.contains("foreign")); assertFalse(serialized.contains("legacy")); assertFalse(serialized.contains("orphan"));
    }
    @Test void leavingTeamImmediatelyRemovesTitlePreviewAndAllMessageCardsFromHttp() throws Exception {
        assertEquals(0, getResult("u", "/api/ai/conversations/ca").path("code").asInt());
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        JsonNode list = getData("u", "/api/ai/conversations"); assertEquals(1, list.size()); assertEquals("b", list.get(0).path("teamId").asText());
        JsonNode denied = getResult("u", "/api/ai/conversations/ca"); assertEquals(40102, denied.path("code").asInt()); assertFalse(denied.toString().contains("secret-ca")); assertFalse(denied.toString().contains("payload-ca"));
        assertEquals(40102, getResult("u", "/api/ai/actions/draft-a").path("code").asInt());
    }
    @Test void currentViewerAndArchivedTeamsRetainPrivateReadAccess() throws Exception {
        jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='a'");
        JsonNode conversation = getData("u", "/api/ai/conversations/ca"); assertTrue(conversation.path("messages").toString().contains("payload-ca"));
        JsonNode draft = getData("u", "/api/ai/actions/draft-a"); assertTrue(draft.path("preview").asText().contains("private-draft"));
    }
    @Test void superAdminCannotReadOtherOwnersPrivateConversationsOrDrafts() throws Exception {
        assertEquals(40404, getResult("root", "/api/ai/conversations/ca").path("code").asInt());
        assertEquals(40305, getResult("root", "/api/ai/actions/draft-a").path("code").asInt());
        JsonNode list = getData("root", "/api/ai/conversations"); assertEquals(1, list.size()); assertEquals("root-own", list.get(0).path("id").asText());
    }
    @Test void legacyUnboundAndMissingTeamsAreNotReadableEvenForSuperAdmin() throws Exception {
        assertEquals(40102, getResult("u", "/api/ai/conversations/legacy").path("code").asInt());
        assertEquals(40102, getResult("root", "/api/ai/conversations/root-legacy").path("code").asInt());
        assertEquals(40102, getResult("root", "/api/ai/actions/root-legacy-draft").path("code").asInt());
        assertEquals(40403, getResult("root", "/api/ai/actions/orphan-draft").path("code").asInt());
    }
    @Test void crossTeamContinuationRejectedBeforeProviderSettingsResourceReadsOrAnyWrites() throws Exception {
        long messages = count("ai_conversation_message"), drafts = count("ai_pending_action");
        JsonNode result = chat("u", "{\"teamId\":\"b\",\"conversationId\":\"ca\",\"message\":\"你好\",\"history\":[{\"role\":\"assistant\",\"content\":\"old-team-context\"}]}");
        assertEquals(40102, result.path("code").asInt()); assertEquals(messages, count("ai_conversation_message")); assertEquals(drafts, count("ai_pending_action"));
        verifyNoInteractions(settings, provider, apis);
    }
    @Test void foreignOwnerAndRevokedMembershipCannotContinueChat() throws Exception {
        assertEquals(40404, chat("root", "{\"teamId\":\"a\",\"conversationId\":\"ca\",\"message\":\"hello\"}").path("code").asInt());
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        assertEquals(40102, chat("u", "{\"teamId\":\"a\",\"conversationId\":\"ca\",\"message\":\"hello\"}").path("code").asInt()); verifyNoInteractions(provider, settings, apis);
    }
    @Test void noTeamChatCannotExpandToAllTeamsIncludingSuperAdmin() throws Exception {
        for (String body : Arrays.asList("{\"message\":\"hello\"}", "{\"teamId\":\" \",\"conversationId\":\"root-own\",\"message\":\"hello\"}")) assertEquals(40001, chat("root", body).path("code").asInt());
        verifyNoInteractions(settings, provider, apis);
    }
    @Test void viewerCanAskAndContinueOwnTeamHistoryWithoutAnyApiWrite() throws Exception {
        long messages = count("ai_conversation_message");
        JsonNode result = chat("u", "{\"teamId\":\"a\",\"conversationId\":\"ca\",\"message\":\"你好\"}");
        assertEquals(0, result.path("code").asInt()); assertEquals("ca", result.path("data").path("conversationId").asText()); assertEquals(messages + 2, count("ai_conversation_message"));
        verifyNoInteractions(provider, apis); assertEquals(0, count("api_definition"));
    }
    @Test void ownerMayCancelNoLongerReadableDraftWithoutPayloadOrTeamWriteRequirement() throws Exception {
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        JsonNode cancelled = json.readTree(mvc.perform(post("/api/ai/actions/draft-a/cancel").header("Authorization", bearer("u"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(0, cancelled.path("code").asInt()); assertEquals("CANCELLED", cancelled.path("data").path("status").asText()); assertEquals(1, cancelled.path("data").size()); assertFalse(cancelled.toString().contains("private-draft"));
    }
    @Test void historyDeleteRemainsOwnerCleanupWithoutLeakingPayloadAfterLeaving() throws Exception {
        jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='a'");
        mvc.perform(delete("/api/ai/conversations/ca").header("Authorization", bearer("u"))).andExpect(jsonPath("$.code").value(0));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation WHERE id='ca'", Integer.class));
    }
    @Test void unauthenticatedHistoryAndPendingReadsNeverExposeContent() throws Exception {
        mvc.perform(get("/api/ai/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ai/actions/draft-a")).andExpect(status().isUnauthorized());
    }

    private void seedConversation(AiConversationRepository repository, String id, String owner, String team) {
        AiConversation c = new AiConversation(); c.setId(id); c.setUserId(owner); c.setTeamId(team); c.setTitle("secret-" + id); c.setCreatedAt("before"); c.setUpdatedAt("before"); repository.insert(c);
        AiConversationMessage m = new AiConversationMessage(); m.setId("m-" + id); m.setConversationId(id); m.setRole("assistant"); m.setContent("secret-" + id); m.setCards("[{\"type\":\"PENDING_ACTION\",\"preview\":\"payload-" + id + "\"}]"); m.setCreatedAt("before"); repository.insertMessage(m);
    }
    private void seedDraft(String id, String owner, String team) {
        jdbc.update("INSERT INTO ai_pending_action(id,user_id,team_id,action_type,payload,summary,status,created_at,expires_at,base_revision,source_type) VALUES(?,?,?,'CREATE_API',?,'private','PENDING','before','2099-01-01T00:00:00Z',0,'WEB')", id, owner, team, "{\"teamId\":\"a\",\"name\":\"private-draft\",\"responseBody\":\"private-draft-body\"}");
    }
    private String bearer(String user) { return "Bearer " + jwt.generateToken(user, user, "root".equals(user) ? "SUPER_ADMIN" : "USER", Collections.emptyList(), 0L, "session-" + user); }
    private JsonNode getResult(String user, String path) throws Exception { return json.readTree(mvc.perform(get(path).header("Authorization", bearer(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()); }
    private JsonNode getData(String user, String path) throws Exception { JsonNode result = getResult(user, path); assertEquals(0, result.path("code").asInt()); return result.path("data"); }
    private JsonNode chat(String user, String body) throws Exception { return json.readTree(mvc.perform(post("/api/ai/chat").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
}
