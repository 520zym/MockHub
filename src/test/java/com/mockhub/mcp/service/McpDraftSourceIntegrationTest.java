package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.*;
import com.mockhub.ai.model.dto.AiApiFullUpdateDraft;
import com.mockhub.ai.repository.AiPendingActionRepository;
import com.mockhub.ai.service.*;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.log.service.LogService;
import com.mockhub.mcp.model.dto.*;
import com.mockhub.mcp.repository.*;
import com.mockhub.mock.model.dto.*;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.repository.*;
import com.mockhub.mock.service.*;
import com.mockhub.stats.service.StatsService;
import com.mockhub.system.controller.HealthController;
import com.mockhub.system.repository.*;
import com.mockhub.system.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Full pending -> aggregate -> revision transaction, real permissions, scoped token and SQLite. */
class McpDraftSourceIntegrationTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;ApiRepository apis;ApiResponseRepository responses;ApiServiceImpl api;PendingActionService pending;McpTokenService tokens;McpAuthService auth;McpToolsService tools;
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup(){
        SQLiteDataSource ds=new SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+temp.resolve("source.db"));new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);jdbc=new JdbcTemplate(ds);
        for(String id:Arrays.asList("u","root"))jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?,'hash',?,?,'before','before')",id,id,id,id.equals("root")?"SUPER_ADMIN":"USER");
        for(String id:Arrays.asList("a","b"))jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES(?,?,?,'before')",id,id,id);
        jdbc.update("INSERT INTO user_team VALUES('u','a','TEAM_ADMIN')");jdbc.update("UPDATE mcp_settings SET enabled=1");
        UserRepository users=new UserRepository();ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);TeamRepository teams=new TeamRepository();ReflectionTestUtils.setField(teams,"jdbcTemplate",jdbc);
        PermissionCheckerImpl permission=new PermissionCheckerImpl();ReflectionTestUtils.setField(permission,"userRepository",users);ReflectionTestUtils.setField(permission,"teamRepository",teams);
        apis=new ApiRepository(jdbc);responses=new ApiResponseRepository(jdbc);
        api=new ApiServiceImpl(apis,responses,new ApiTagRepository(jdbc),new GroupRepository(jdbc),new TagRepository(jdbc),mock(TeamService.class),permission,json,mock(LogService.class),users,new MockFileStorageService(temp.toString()));
        pending=new PendingActionService(new AiPendingActionRepository(jdbc),json,api,permission,mock(LogService.class));
        McpScopeService owners=new McpScopeService(users,teams);McpTokenRepository tokenRepo=new McpTokenRepository(jdbc);tokens=new McpTokenService(tokenRepo);ReflectionTestUtils.setField(tokens,"scopeService",owners);
        McpSettingsService settings=new McpSettingsService(new McpSettingsRepository(jdbc));McpToolRegistry registry=new McpToolRegistry(json);McpResourceScopeRepository resources=new McpResourceScopeRepository(jdbc);
        McpExecutionPolicyService execution=new McpExecutionPolicyService(tokenRepo,owners,resources,settings,registry,json);
        ReflectionTestUtils.setField(pending,"sourceGuard",new McpDraftSourceGuard(tokenRepo,owners,execution,resources,json));
        auth=new McpAuthService(tokens,new CurrentUserProviderImpl(users,new AuthSessionRepository(jdbc)));
        tools=new McpToolsService(json,api,mock(StatsService.class),mock(AiService.class),mock(AiExplainService.class),pending,permission,settings,mock(HealthController.class));web("u");
    }
    @AfterEach void close(){auth.clear();}
    void web(String id){Map<String,Object>d=new HashMap<>();d.put("userId",id);d.put("username",id);d.put("globalRole",id.equals("root")?"SUPER_ADMIN":"USER");UsernamePasswordAuthenticationToken a=new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList());a.setDetails(d);SecurityContextHolder.getContext().setAuthentication(a);}
    McpTokenCreateResult mint(String owner,String...scopes){web(owner);McpTokenCreateRequest r=new McpTokenCreateRequest();r.setName("Source");r.setTeamIds(Arrays.asList("a"));r.setScopes(Arrays.asList(scopes));return tokens.create(owner,r);}
    void mcp(String raw){MockHttpServletRequest r=new MockHttpServletRequest();r.addHeader("Authorization","Bearer "+raw);auth.authenticate(r);}
    String draft(McpTokenCreateResult token,String path){mcp(token.getToken());Map<?,?> result=(Map<?,?>)tools.execute(McpToolRegistry.CREATE_API_DRAFT,json.createObjectNode().put("teamId","a").put("name","MCP").put("method","GET").put("path",path).put("responseBody","{}"));return result.get("actionId").toString();}
    ApiDefinitionDTO dto(String path){ApiDefinitionDTO d=new ApiDefinitionDTO();d.setTeamId("a");d.setName("API");d.setMethod("GET");d.setPath(path);d.setEnabled(true);d.setResponseBody("{}");ApiResponseDTO r=new ApiResponseDTO();r.setName("Default");r.setResponseBody("{}");r.setActive(true);d.setResponses(Collections.singletonList(r));return d;}
    void noClaimOrWrites(String id){assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ai_pending_action WHERE id=?",String.class,id));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM api_definition",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM api_revision",Integer.class));}
    @Test void revokedSourceTokenCannotBeLaunderedThroughWebConfirmation(){
        McpTokenCreateResult token=mint("u","DRAFT_CREATE");String id=draft(token,"/revoked");assertEquals("MCP",jdbc.queryForObject("SELECT source_type FROM ai_pending_action WHERE id=?",String.class,id));assertEquals(token.getId(),jdbc.queryForObject("SELECT source_token_id FROM ai_pending_action WHERE id=?",String.class,id));
        web("u");tokens.revoke(token.getId());assertEquals(40926,assertThrows(BizException.class,()->pending.confirm(id)).getCode());noClaimOrWrites(id);
    }
    @Test void expiredOrDisabledOwnerSourceCannotBeConfirmedEvenByWebSuper(){
        McpTokenCreateResult token=mint("u","DRAFT_CREATE");String id=draft(token,"/expired");jdbc.update("UPDATE mcp_token SET expires_at=? WHERE id=?",java.time.Instant.now().minusSeconds(1).toString(),token.getId());web("root");assertEquals(40926,assertThrows(BizException.class,()->pending.confirm(id)).getCode());noClaimOrWrites(id);
        jdbc.update("UPDATE mcp_token SET expires_at=? WHERE id=?",java.time.Instant.now().plusSeconds(3600).toString(),token.getId());jdbc.update("UPDATE user SET status='DISABLED' WHERE id='u'");assertEquals(40926,assertThrows(BizException.class,()->pending.confirm(id)).getCode());noClaimOrWrites(id);
    }
    @Test void currentScopeOwnerRoleAndGlobalToolPolicyAreAllRecheckedUnderWriteTransaction(){
        McpTokenCreateResult token=mint("u","DRAFT_CREATE","API_READ");String id=draft(token,"/intersection");web("u");
        jdbc.update("UPDATE mcp_token SET scopes=? WHERE id=?","{\"a\":[\"API_READ\"]}",token.getId());assertThrows(BizException.class,()->pending.confirm(id));noClaimOrWrites(id);
        jdbc.update("UPDATE mcp_token SET scopes=? WHERE id=?","{\"a\":[\"DRAFT_CREATE\"]}",token.getId());jdbc.update("UPDATE user_team SET role='VIEWER' WHERE user_id='u'");assertThrows(BizException.class,()->pending.confirm(id));noClaimOrWrites(id);
        jdbc.update("UPDATE user_team SET role='TEAM_ADMIN' WHERE user_id='u'");jdbc.update("UPDATE mcp_settings SET tool_create_api_draft=0");assertThrows(BizException.class,()->pending.confirm(id));noClaimOrWrites(id);
        jdbc.update("UPDATE mcp_settings SET tool_create_api_draft=1,enabled=0");assertThrows(BizException.class,()->pending.confirm(id));noClaimOrWrites(id);
    }
    @Test void webManualDraftOnlyConfirmationPreservesMcpProvenanceAndDoesNotRequireDirectApply(){
        McpTokenCreateResult token=mint("u","DRAFT_CREATE");String id=draft(token,"/web-confirm");web("u");Map<String,Object> confirmed=pending.confirm(id);assertEquals("CONFIRMED",confirmed.get("status"));assertEquals("MCP",api.revisions(confirmed.get("apiId").toString()).get(0).get("source"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM api_definition",Integer.class));
    }
    @Test void laterAddedApplyScopeOrAnotherTokenCannotExpandOriginSnapshot(){
        McpTokenCreateResult token=mint("u","DRAFT_CREATE");String id=draft(token,"/no-expand");jdbc.update("UPDATE mcp_token SET scopes=? WHERE id=?","{\"a\":[\"DRAFT_CREATE\",\"DRAFT_APPLY\"]}",token.getId());jdbc.update("UPDATE mcp_settings SET write_mode='DIRECT',tool_apply_api_draft=1");mcp(token.getToken());assertEquals(40926,assertThrows(BizException.class,()->tools.execute(McpToolRegistry.APPLY_API_DRAFT,json.createObjectNode().put("actionId",id))).getCode());noClaimOrWrites(id);
        McpTokenCreateResult another=mint("u","DRAFT_CREATE","DRAFT_APPLY");mcp(another.getToken());assertEquals(40926,assertThrows(BizException.class,()->tools.execute(McpToolRegistry.APPLY_API_DRAFT,json.createObjectNode().put("actionId",id))).getCode());noClaimOrWrites(id);
    }
    @Test void readonlySnapshotCannotGainWriteThroughCurrentTokenOrWebEntry(){
        McpTokenCreateResult token=mint("u","API_READ");mcp(token.getToken());assertThrows(BizException.class,()->pending.createApiDraft(dto("/readonly"),"No"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ai_pending_action",Integer.class));
        web("u");String id=pending.createApiDraft(dto("/tampered"),"Web").get("actionId").toString();
        String snapshot="{\"version\":1,\"teamId\":\"a\",\"actionScope\":\"DRAFT_CREATE\",\"targetApiId\":null,\"teamIds\":[\"a\"],\"teamScopes\":{\"a\":[\"API_READ\"]}}";
        jdbc.update("UPDATE ai_pending_action SET source_type='MCP',source_token_id=?,source_scope_json=? WHERE id=?",token.getId(),snapshot,id);jdbc.update("UPDATE mcp_token SET scopes=? WHERE id=?","{\"a\":[\"API_READ\",\"DRAFT_CREATE\"]}",token.getId());assertEquals(40926,assertThrows(BizException.class,()->pending.confirm(id)).getCode());noClaimOrWrites(id);
    }
    @Test void directApplyRequiresSameOriginTokenAndCurrentDirectPolicy(){
        McpTokenCreateResult token=mint("u","DRAFT_CREATE","DRAFT_APPLY");String id=draft(token,"/direct");mcp(token.getToken());assertThrows(BizException.class,()->tools.execute(McpToolRegistry.APPLY_API_DRAFT,json.createObjectNode().put("actionId",id)));noClaimOrWrites(id);
        jdbc.update("UPDATE mcp_settings SET write_mode='DIRECT',tool_apply_api_draft=1");Map<?,?> result=(Map<?,?>)tools.execute(McpToolRegistry.APPLY_API_DRAFT,json.createObjectNode().put("actionId",id));assertEquals("CONFIRMED",result.get("status"));assertEquals("MCP",api.revisions(result.get("apiId").toString()).get(0).get("source"));
    }
    @Test void sourceValidationPrecedesClaimAndAllWritesStillRollbackOnHistoryFailure(){
        McpTokenCreateResult token=mint("u","DRAFT_CREATE");String id=draft(token,"/atomic");web("u");jdbc.execute("CREATE TRIGGER deny_source_history BEFORE INSERT ON api_revision BEGIN SELECT RAISE(ABORT,'history fail'); END");assertThrows(BizException.class,()->pending.confirm(id));noClaimOrWrites(id);
        jdbc.execute("DROP TRIGGER deny_source_history");assertEquals("CONFIRMED",pending.confirm(id).get("status"));
    }
    @Test void revokedMcpDraftIsRejectedByActualWebJwtConfirmEndpoint() throws Exception {
        McpTokenCreateResult token=mint("u","DRAFT_CREATE");String id=draft(token,"/web-jwt");
        jdbc.update("UPDATE mcp_token SET revoked_at='revoked' WHERE id=?",token.getId());
        com.mockhub.common.util.JwtUtil jwt=new com.mockhub.common.util.JwtUtil();jwt.init();
        jdbc.update("INSERT INTO auth_session(id,user_id,auth_version,created_at,expires_at) VALUES('sid','u',0,'now',?)",System.currentTimeMillis()+3600000);
        UserRepository users=new UserRepository();ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);
        com.mockhub.common.filter.JwtAuthFilter filter=new com.mockhub.common.filter.JwtAuthFilter(jwt,json,new CurrentUserProviderImpl(users,new AuthSessionRepository(jdbc)));
        com.mockhub.ai.controller.AiController controller=new com.mockhub.ai.controller.AiController(mock(AiSettingsService.class),mock(AiModelCatalogService.class),mock(AiService.class),mock(AiExplainService.class),mock(AiChatService.class),mock(AiConversationService.class),pending,mock(LogService.class));
        org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new com.mockhub.common.model.GlobalExceptionHandler()).addFilters(filter).build();
        String signed=jwt.generateToken("u","u","USER",Arrays.asList("a"),0L,"sid");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ai/actions/"+id).header("Authorization","Bearer "+signed)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/actions/"+id+"/confirm").header("Authorization","Bearer "+signed)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(40926));
        noClaimOrWrites(id);
    }
    @Test void updateDraftOriginBindsRealTargetAndRevocationDoesNotAlterExistingAggregate() {
        web("u");ApiDefinition original=api.create(dto("/update-source"));
        McpTokenCreateResult token=mint("u","DRAFT_UPDATE");mcp(token.getToken());
        Map<?,?> draft=(Map<?,?>)tools.execute(McpToolRegistry.UPDATE_API_DRAFT,json.createObjectNode().put("apiId",original.getId()).put("name","Scoped update"));String id=draft.get("actionId").toString();
        assertEquals(original.getId(),jdbc.queryForObject("SELECT target_api_id FROM ai_pending_action WHERE id=?",String.class,id));
        assertTrue(jdbc.queryForObject("SELECT source_scope_json FROM ai_pending_action WHERE id=?",String.class,id).contains("DRAFT_UPDATE"));
        jdbc.update("UPDATE mcp_token SET revoked_at='revoked' WHERE id=?",token.getId());web("u");assertThrows(BizException.class,()->pending.confirm(id));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ai_pending_action WHERE id=?",String.class,id));assertEquals(1,apis.findById(original.getId()).getRevision());assertEquals(1,api.revisions(original.getId()).size());
        McpTokenCreateResult fresh=mint("u","DRAFT_UPDATE");mcp(fresh.getToken());Map<?,?> proposal=(Map<?,?>)tools.execute(McpToolRegistry.UPDATE_API_DRAFT,json.createObjectNode().put("apiId",original.getId()).put("name","Fresh scoped update"));web("u");
        assertEquals("CONFIRMED",pending.confirm(proposal.get("actionId").toString()).get("status"));assertEquals(2,apis.findById(original.getId()).getRevision());
    }

    @Test void unboundLegacyAndForgedResourceTeamNeedRegenerationOrRejection(){
        web("u");String id=pending.createApiDraft(dto("/legacy"),"Web").get("actionId").toString();jdbc.update("UPDATE ai_pending_action SET source_type='LEGACY' WHERE id=?",id);assertEquals(40926,assertThrows(BizException.class,()->pending.confirm(id)).getCode());noClaimOrWrites(id);
        McpTokenCreateResult token=mint("u","DRAFT_CREATE");String target=draft(token,"/wrong-team");String payload=jdbc.queryForObject("SELECT payload FROM ai_pending_action WHERE id=?",String.class,target);jdbc.update("UPDATE ai_pending_action SET payload=? WHERE id=?",payload.replace("\"teamId\":\"a\"","\"teamId\":\"b\""),target);web("root");assertThrows(BizException.class,()->pending.confirm(target));noClaimOrWrites(target);
    }
}
