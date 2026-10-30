package com.mockhub.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.model.AiConversationMessage;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.AiChatRequest;
import com.mockhub.ai.model.dto.AiChatResponse;
import com.mockhub.ai.model.dto.AiResponseResult;
import com.mockhub.ai.repository.AiConversationRepository;
import com.mockhub.ai.repository.AiPendingActionRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.repository.*;
import com.mockhub.mock.service.ApiServiceImpl;
import com.mockhub.mock.service.MockFileStorageService;
import com.mockhub.stats.service.StatsService;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.PermissionCheckerImpl;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual SQLite/permissions/API/history/pending services; only provider generation is stubbed. */
class AiCrossTeamHistoryIntegrationTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate jdbc;
    private ApiServiceImpl apis;
    private AiConversationRepository conversations;
    private AiConversationService history;
    private AiSettingsService settings;
    private OpenAiCompatibleProvider provider;
    private AiService generation;
    private AiSettings config;
    private AiChatService chat;
    private String aApi, bApi;

    @BeforeEach void setup() {
        SQLiteDataSource ds = new SQLiteDataSource(); ds.setUrl("jdbc:sqlite:" + temp.resolve("chat.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds); jdbc = new JdbcTemplate(ds);
        for (String id : Arrays.asList("u", "root")) jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?,'hash',?,?,'before','before')", id,id,id,"root".equals(id)?"SUPER_ADMIN":"USER");
        for (String id : Arrays.asList("a", "b")) jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES(?,?,?,'before')",id,id,id);
        jdbc.update("INSERT INTO user_team VALUES('u','a','VIEWER'),('u','b','EDITOR')");
        UserRepository users = new UserRepository(); ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);
        TeamRepository teams = new TeamRepository(); ReflectionTestUtils.setField(teams,"jdbcTemplate",jdbc);
        PermissionCheckerImpl permissions = new PermissionCheckerImpl(); ReflectionTestUtils.setField(permissions,"userRepository",users); ReflectionTestUtils.setField(permissions,"teamRepository",teams);
        apis = new ApiServiceImpl(new ApiRepository(jdbc),new ApiResponseRepository(jdbc),new ApiTagRepository(jdbc),new GroupRepository(jdbc),new TagRepository(jdbc),mock(TeamService.class),permissions,json,mock(LogService.class),users,new MockFileStorageService(temp.toString()));
        login("root"); aApi = apis.create(api("a", "A_CONTENT")).getId(); bApi = apis.create(api("b", "B_ONLY_SECRET")).getId();
        conversations = new AiConversationRepository(jdbc); history = new AiConversationService(conversations,json,permissions,teams);
        PendingActionService pending = new PendingActionService(new AiPendingActionRepository(jdbc),json,apis,permissions,mock(LogService.class));
        settings = mock(AiSettingsService.class); provider = mock(OpenAiCompatibleProvider.class); generation = mock(AiService.class);
        config = new AiSettings(); config.setEnabled(true);config.setProvider(AiSettings.PROVIDER_MOCK);config.setCapDrafts(true);config.setCapResponseBody(true);
        when(settings.assertUsable(any(),any())).thenReturn(config);
        AiResponseResult result = new AiResponseResult(); result.setContent("{\"updated\":true}"); when(generation.generateResponse(any())).thenReturn(result);
        chat = new AiChatService(settings,provider,mock(MockAiGenerator.class),generation,apis,mock(StatsService.class),pending,history,json);
        login("u");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private ApiDefinitionDTO api(String team, String body) {
        ApiDefinitionDTO dto = new ApiDefinitionDTO(); dto.setTeamId(team);dto.setType("REST");dto.setName(team+" API");dto.setPath("/"+team);dto.setMethod("GET");dto.setEnabled(true);dto.setResponseBody(body);dto.setContentType("application/json");dto.setResponseCode(200);
        ApiResponseDTO response = new ApiResponseDTO();response.setName("Default");response.setActive(true);response.setBodyType("TEXT");response.setResponseBody(body);response.setContentType("application/json");response.setResponseCode(200);dto.setResponses(Collections.singletonList(response));return dto;
    }
    private void login(String id) {
        Map<String,Object> details=new HashMap<String,Object>();details.put("userId",id);details.put("username",id);details.put("globalRole","root".equals(id)?"SUPER_ADMIN":"USER");
        UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList());auth.setDetails(details);SecurityContextHolder.getContext().setAuthentication(auth);
    }
    private AiChatRequest request(String team, String api) { AiChatRequest r=new AiChatRequest();r.setTeamId(team);r.setContextApiId(api);r.setMessage("修改返回值");return r; }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    private AiConversation seed(String id,String team,String cards) {
        AiConversation c=new AiConversation();c.setId(id);c.setTeamId(team);c.setUserId("u");c.setTitle("PRIVATE_TITLE_"+id);c.setCreatedAt("before");c.setUpdatedAt("before");conversations.insert(c);
        AiConversationMessage m=new AiConversationMessage();m.setId(id+"-m");m.setConversationId(id);m.setRole("assistant");m.setContent("PRIVATE_PREVIEW_"+id);m.setCards(cards);m.setCreatedAt("before");conversations.insertMessage(m);return c;
    }
    private void assertQuarantined(String id) {
        assertEquals(40102,assertThrows(BizException.class,()->history.detail(id,"u")).getCode());
        assertFalse(history.list("u").stream().anyMatch(c->id.equals(c.getId())));
        assertEquals(40102,assertThrows(BizException.class,()->history.validateContext(id,"u","a")).getCode());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ai_conversation_message WHERE conversation_id=?",Integer.class,id));
    }

    @Test void crossTeamContextIsRejectedBeforeSettingsProviderDraftOrHistory() {
        assertEquals(40102,assertThrows(BizException.class,()->chat.chat(request("a",bApi))).getCode());
        verifyNoInteractions(settings,provider,generation);
        assertEquals(0,count("ai_pending_action"));assertEquals(0,count("ai_conversation"));assertEquals(0,count("ai_conversation_message"));
        assertEquals("B_ONLY_SECRET",apis.getById(bApi).getResponseBody());
    }
    @Test void superAdminMustAlsoRespectSelectedWorkspace() {
        login("root");assertEquals(40102,assertThrows(BizException.class,()->chat.chat(request("a",bApi))).getCode());verifyNoInteractions(settings,provider,generation);
    }
    @Test void modelSelectedForeignApiCannotOverrideTheSelectedWorkspace() {
        config.setProvider("OPENAI");when(provider.complete(any(),anyString(),anyString())).thenReturn("{\"intent\":\"UPDATE_API_DRAFT\",\"apiId\":\""+bApi+"\"}");
        assertEquals(40102,assertThrows(BizException.class,()->chat.chat(request("a",null))).getCode());
        verify(provider).complete(any(),anyString(),anyString());verifyNoInteractions(generation);assertEquals(0,count("ai_pending_action"));assertEquals(0,count("ai_conversation"));
    }
    @Test void matchingTeamStillGeneratesOriginalRevisionBoundDraftAndReadableHistory() {
        AiChatResponse result=chat.chat(request("b",bApi));
        assertEquals(1,count("ai_pending_action"));assertEquals(1,count("ai_conversation"));
        assertTrue(json.valueToTree(history.detail(result.getConversationId(),"u")).toString().contains("B_ONLY_SECRET"));
        assertEquals(1L,jdbc.queryForObject("SELECT base_revision FROM ai_pending_action",Long.class));
    }
    @Test void existingMixedPendingCardStaysHiddenAfterLeavingForeignTeamWithoutDeletingData() {
        seed("mixed","a","[{\"type\":\"PENDING_ACTION\",\"actionId\":\"expired-old-id\",\"summary\":{\"teamId\":\"b\"},\"oldPreview\":\"B_ONLY_SECRET\"}]");
        assertQuarantined("mixed");jdbc.update("DELETE FROM user_team WHERE user_id='u' AND team_id='b'");
        assertEquals(40102,assertThrows(BizException.class,()->apis.getById(bApi)).getCode());assertQuarantined("mixed");
        assertTrue(jdbc.queryForObject("SELECT cards FROM ai_conversation_message WHERE conversation_id='mixed'",String.class).contains("B_ONLY_SECRET"));
        history.delete("mixed","u");assertEquals(0,count("ai_conversation"));assertEquals(2,count("api_definition"));
    }
    @Test void actualApiOwnershipOverridesMissingOrForgedCardMetadata() {
        seed("api-mixed","a","[{\"type\":\"API_LIST\",\"teamId\":\"a\",\"items\":[{\"apiId\":\""+bApi+"\",\"teamId\":\"a\"}]}]");assertQuarantined("api-mixed");
        seed("stats-mixed","a","[{\"type\":\"STATS\",\"items\":[{\"apiId\":\""+bApi+"\"}]}]");assertQuarantined("stats-mixed");
    }
    @Test void pendingRowOwnershipOverridesStaleCardSummary() {
        jdbc.update("INSERT INTO ai_pending_action(id,user_id,team_id,action_type,status,payload,created_at,expires_at,base_revision,source_type) VALUES('foreign-action','u','b','CREATE_API','PENDING','{}','before','2999-01-01T00:00:00',0,'WEB')");
        seed("action-mixed","a","[{\"type\":\"PENDING_ACTION\",\"summary\":{\"teamId\":\"a\"},\"actionId\":\"foreign-action\"}]");assertQuarantined("action-mixed");
    }
    @Test void malformedStoredCardCannotExposeItsMessageTitleOrPreview() {
        seed("bad-json","a","not-json");assertQuarantined("bad-json");
        seed("wrong-shape","a","{\"oldPreview\":\"secret\"}");assertQuarantined("wrong-shape");
    }
    @Test void appendRejectsMixedCardsBeforeCreatingAnyRows() {
        Map<String,Object> card=new HashMap<String,Object>();card.put("type","PENDING_ACTION");card.put("summary",Collections.singletonMap("teamId","b"));
        assertEquals(40102,assertThrows(BizException.class,()->history.appendExchange(null,"u","a","question","reply",Collections.singletonList(card),"mock","mock")).getCode());
        assertEquals(0,count("ai_conversation"));assertEquals(0,count("ai_conversation_message"));
    }
    @Test void validLegacyCardsAndBusinessPayloadFieldsRemainReadableAndUnchanged() throws Exception {
        String cards="[{\"private\":\"legacy custom card\"},{\"type\":\"STATS\",\"items\":[{\"apiId\":\""+aApi+"\"}]},{\"type\":\"PENDING_ACTION\",\"actionId\":\"purged-action\",\"summary\":{\"teamId\":\"a\"},\"preview\":\"{\\\"teamId\\\":\\\"b\\\",\\\"apiId\\\":\\\"business-key\\\"}\"}]";
        seed("legacy-safe","a",cards);JsonNode detail=json.valueToTree(history.detail("legacy-safe","u"));
        assertEquals(json.readTree(cards),detail.path("messages").get(0).path("cards"));assertEquals(1,history.list("u").size());
        history.validateContext("legacy-safe","u","a");assertEquals(cards,jdbc.queryForObject("SELECT cards FROM ai_conversation_message",String.class));
    }
}
