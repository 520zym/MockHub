package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.*;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.common.model.*;
import com.mockhub.common.util.*;
import com.mockhub.log.service.LogService;
import com.mockhub.mcp.controller.McpEndpointController;
import com.mockhub.mcp.model.*;
import com.mockhub.mcp.model.dto.*;
import com.mockhub.mcp.repository.*;
import com.mockhub.system.controller.HealthController;
import com.mockhub.system.model.entity.*;
import com.mockhub.system.repository.*;
import com.mockhub.system.service.CurrentUserProviderImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** SQLite-backed token, owner facts, scope matrix, resource metadata, settings and MCP HTTP are real.
 * Only domain payload execution and audit output are mocked to isolate the authorization boundary. */
class McpScopeRuntimeIntegrationTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;McpTokenService tokens;McpTokenRepository tokenRepo;McpAuthService auth;McpExecutionPolicyService policy;McpToolsService tools;MockMvc mvc;
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup(){
        SQLiteDataSource ds=new SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+temp.resolve("runtime.db"));new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);jdbc=new JdbcTemplate(ds);
        for(String id:Arrays.asList("owner","super"))jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?,'hash',?,?,'before','before')",id,id,id,id.equals("super")?"SUPER_ADMIN":"USER");
        for(String id:Arrays.asList("edit","view","hidden"))jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES(?,?,?,'before')",id,id,id);
        jdbc.update("INSERT INTO user_team VALUES('owner','edit','EDITOR'),('owner','view','VIEWER')");
        for(String id:Arrays.asList("edit","view","hidden"))jdbc.update("INSERT INTO api_definition(id,team_id,name,method,path,created_at,updated_at) VALUES(?,?,?,'GET','/path','before','before')","api-"+id,id,id);
        jdbc.update("INSERT INTO request_log(id,team_id,created_at) VALUES('log-hidden','hidden','before')");
        jdbc.update("UPDATE mcp_settings SET enabled=1");
        UserRepository users=new UserRepository();ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);TeamRepository teams=new TeamRepository();ReflectionTestUtils.setField(teams,"jdbcTemplate",jdbc);
        McpScopeService owners=new McpScopeService(users,teams);tokenRepo=new McpTokenRepository(jdbc);tokens=new McpTokenService(tokenRepo);ReflectionTestUtils.setField(tokens,"scopeService",owners);
        McpSettingsService settings=new McpSettingsService(new McpSettingsRepository(jdbc));McpToolRegistry registry=new McpToolRegistry(json);
        policy=new McpExecutionPolicyService(tokenRepo,owners,new McpResourceScopeRepository(jdbc),settings,registry,json);
        auth=new McpAuthService(tokens,new CurrentUserProviderImpl(users,new AuthSessionRepository(jdbc)));
        tools=mock(McpToolsService.class);when(tools.execute(anyString(),any())).thenAnswer(call->{Map<String,Object>r=new LinkedHashMap<>();r.put("teamId",((JsonNode)call.getArgument(1)).path("teamId").asText("GLOBAL_LEAK"));r.put("tool",call.getArgument(0));return r;});
        McpServerService server=new McpServerService(json,settings,registry,tools,mock(LogService.class),policy);
        mvc=MockMvcBuilders.standaloneSetup(new McpEndpointController(json,auth,settings,server,mock(HealthController.class))).build();
    }
    @AfterEach void close(){auth.clear();}
    void web(String id){Map<String,Object>d=new HashMap<>();d.put("userId",id);d.put("username",id);d.put("globalRole",id.equals("super")?"SUPER_ADMIN":"USER");UsernamePasswordAuthenticationToken a=new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList());a.setDetails(d);SecurityContextHolder.getContext().setAuthentication(a);}
    McpTokenCreateResult mint(String id,Map<String,List<String>> matrix){web(id);McpTokenCreateRequest r=new McpTokenCreateRequest();r.setName("Test");r.setTeamIds(new ArrayList<>(matrix.keySet()));Set<String>s=new LinkedHashSet<>();for(List<String>v:matrix.values())s.addAll(v);r.setScopes(new ArrayList<>(s));r.setTeamScopes(matrix);return tokens.create(id,r);}
    Map<String,List<String>> matrix(String team,String...scopes){Map<String,List<String>>m=new LinkedHashMap<>();m.put(team,Arrays.asList(scopes));return m;}
    JsonNode rpc(String raw,String method,String tool,String args)throws Exception{Map<String,Object>r=new LinkedHashMap<>();r.put("jsonrpc","2.0");r.put("id",1);r.put("method",method);if(tool!=null){Map<String,Object>p=new LinkedHashMap<>();p.put("name",tool);p.put("arguments",json.readTree(args));r.put("params",p);}return json.readTree(mvc.perform(post("/mcp").header("Authorization","Bearer "+raw).contentType("application/json").content(json.writeValueAsString(r))).andReturn().getResponse().getContentAsString());}
    boolean failed(JsonNode r){return r.has("error")||r.path("result").path("isError").asBoolean();}
    @Test void readonlySuperTokenCannotEscapeExplicitTeamOrScopeEvenWithForgedTeamId()throws Exception{
        String raw=mint("super",matrix("edit","API_READ")).getToken();JsonNode list=rpc(raw,"tools/list",null,null);assertEquals(2,list.path("result").path("tools").size());
        assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.GET_API_DETAIL,"{\"apiId\":\"api-hidden\",\"teamId\":\"edit\"}")));
        assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.CREATE_API_DRAFT,"{\"teamId\":\"edit\"}")));
        verifyNoInteractions(tools);
        assertFalse(failed(rpc(raw,"tools/call",McpToolRegistry.GET_API_DETAIL,"{\"apiId\":\"api-edit\"}")));
        verify(tools).execute(eq(McpToolRegistry.GET_API_DETAIL),argThat(a->a.path("teamId").asText().equals("edit")));
    }
    @Test void omittedSearchAndStatsTeamNeverMeansGlobalAndMixedTeamWriteScopesStayIndependent()throws Exception{
        Map<String,List<String>>m=matrix("edit","API_READ","STATS_READ","DRAFT_CREATE");m.put("view",Arrays.asList("API_READ","STATS_READ"));String raw=mint("owner",m).getToken();
        String searched=rpc(raw,"tools/call",McpToolRegistry.SEARCH_APIS,"{\"query\":\"\"}").toString();assertTrue(searched.contains("edit"));assertTrue(searched.contains("view"));assertFalse(searched.contains("hidden"));assertFalse(searched.contains("GLOBAL_LEAK"));
        assertFalse(failed(rpc(raw,"tools/call",McpToolRegistry.GET_API_STATS,"{}")));
        assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.CREATE_API_DRAFT,"{\"teamId\":\"view\"}")));
        assertFalse(failed(rpc(raw,"tools/call",McpToolRegistry.CREATE_API_DRAFT,"{\"teamId\":\"edit\"}")));
        verify(tools,never()).execute(anyString(),argThat(a->a.path("teamId").asText().isEmpty()||a.path("teamId").asText().equals("hidden")));
    }
    @Test void instancePolicyFiltersListingAndRejectsDisabledToolsAndDirectApply()throws Exception{
        String raw=mint("owner",matrix("edit","API_READ","DRAFT_APPLY")).getToken();
        jdbc.update("UPDATE mcp_settings SET tool_search_apis=0,tool_apply_api_draft=1");JsonNode list=rpc(raw,"tools/list",null,null);assertEquals(1,list.path("result").path("tools").size());assertEquals(McpToolRegistry.GET_API_DETAIL,list.path("result").path("tools").get(0).path("name").asText());
        assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.SEARCH_APIS,"{}")));assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.APPLY_API_DRAFT,"{\"actionId\":\"a\"}")));verifyNoInteractions(tools);
        jdbc.update("UPDATE mcp_settings SET write_mode='DIRECT'");assertEquals(2,rpc(raw,"tools/list",null,null).path("result").path("tools").size());
        jdbc.update("UPDATE mcp_settings SET enabled=0");assertTrue(failed(rpc(raw,"tools/list",null,null)));
    }
    @Test void liveDemotionRemovalArchiveOwnerDisableRevokeAndExpireReduceAccessImmediately()throws Exception{
        McpTokenCreateResult issued=mint("super",matrix("hidden","API_READ","DRAFT_CREATE"));String raw=issued.getToken();
        assertFalse(failed(rpc(raw,"tools/call",McpToolRegistry.CREATE_API_DRAFT,"{\"teamId\":\"hidden\"}")));
        jdbc.update("UPDATE user SET global_role='USER' WHERE id='super'");assertEquals(0,rpc(raw,"tools/list",null,null).path("result").path("tools").size());assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.GET_API_DETAIL,"{\"apiId\":\"api-hidden\"}")));
        issued=mint("owner",matrix("edit","API_READ","DRAFT_UPDATE"));raw=issued.getToken();jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='edit'");assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.UPDATE_API_DRAFT,"{\"apiId\":\"api-edit\"}")));assertFalse(failed(rpc(raw,"tools/call",McpToolRegistry.GET_API_DETAIL,"{\"apiId\":\"api-edit\"}")));
        jdbc.update("DELETE FROM user_team WHERE user_id='owner' AND team_id='edit'");assertEquals(0,rpc(raw,"tools/list",null,null).path("result").path("tools").size());
        jdbc.update("UPDATE user SET status='DISABLED' WHERE id='owner'");assertTrue(failed(rpc(raw,"tools/list",null,null)));jdbc.update("UPDATE user SET status='ACTIVE' WHERE id='owner'");
        jdbc.update("UPDATE mcp_token SET revoked_at='now' WHERE id=?",issued.getId());assertTrue(failed(rpc(raw,"tools/list",null,null)));
        jdbc.update("UPDATE mcp_token SET revoked_at=NULL,expires_at=? WHERE id=?",java.time.Instant.now().minusSeconds(1).toString(),issued.getId());assertTrue(failed(rpc(raw,"tools/list",null,null)));
    }
    @Test void resourceTargetsComeFromApiAndLogNotCallerParametersAndJwtCannotForgeContext()throws Exception{
        String raw=mint("owner",matrix("edit","DRAFT_UPDATE","AI_GENERATE")).getToken();assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.UPDATE_API_DRAFT,"{\"apiId\":\"api-hidden\",\"teamId\":\"edit\"}")));assertTrue(failed(rpc(raw,"tools/call",McpToolRegistry.EXPLAIN_MATCH,"{\"requestLogId\":\"log-hidden\",\"teamId\":\"edit\"}")));verifyNoInteractions(tools);
        web("super");SecurityContextUtil.getUserInfo().put(McpRequestContext.KEY,"forged-token-context");assertThrows(BizException.class,()->policy.assertTool(McpToolRegistry.SEARCH_APIS,json.createObjectNode()));
    }
    @Test void scopeChangesAfterAuthenticationCannotExpandTheTrustedSnapshot()throws Exception{
        McpTokenCreateResult issued=mint("owner",matrix("edit","API_READ"));MockHttpServletRequest req=new MockHttpServletRequest();req.addHeader("Authorization","Bearer "+issued.getToken());auth.authenticate(req);
        jdbc.update("UPDATE mcp_token SET scopes=? WHERE id=?","{\"edit\":[\"API_READ\",\"DRAFT_CREATE\"]}",issued.getId());assertThrows(BizException.class,()->policy.assertTool(McpToolRegistry.CREATE_API_DRAFT,json.createObjectNode().put("teamId","edit")));
        jdbc.update("UPDATE mcp_token SET scopes=? WHERE id=?","{\"edit\":[\"DRAFT_CREATE\"]}",issued.getId());assertThrows(BizException.class,()->policy.assertTool(McpToolRegistry.SEARCH_APIS,json.createObjectNode().put("teamId","edit")));
    }
}
