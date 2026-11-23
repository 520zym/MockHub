package com.mockhub.log.service;

import com.fasterxml.jackson.databind.*;
import com.mockhub.ai.model.AiSettings;
import com.mockhub.ai.model.dto.*;
import com.mockhub.ai.service.*;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.common.config.LogRetainProperties;
import com.mockhub.common.filter.JwtAuthFilter;
import com.mockhub.common.model.*;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.log.controller.LogController;
import com.mockhub.log.model.*;
import com.mockhub.log.repository.LogRepository;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.*;
import com.mockhub.mock.service.*;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.stats.service.StatsService;
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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Authorization, log snapshots, API details and live matcher all use real temporary SQLite. */
class LogPrivacyIntegrationTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;LogRepository repository;LogServiceImpl logs;UserRepository users;PermissionCheckerImpl permission;ApiServiceImpl apis;ApiResponseRepository responses;MockMvc mvc;JwtUtil jwt;
    final ObjectMapper json=new ObjectMapper();
    final String headers="{\"Authorization\":\"Bearer HEADER_SECRET\",\"Proxy-Authorization\":\"Basic PROXY_SECRET\",\"Cookie\":\"sid=COOKIE_SECRET\",\"Set-Cookie\":\"SET_COOKIE_SECRET\",\"X-API-Key\":\"XKEY_SECRET\",\"API-Key\":\"APIKEY_SECRET\",\"X-Env\":\"prod\"}";
    final String body="{\"password\":\"BODY_SECRET\",\"nested\":{\"token\":\"NESTED_SECRET\",\"orderId\":123},\"business\":\"normal\"}";
    @BeforeEach void setup(){
        SQLiteDataSource ds=new SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+temp.resolve("privacy.db"));new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);jdbc=new JdbcTemplate(ds);
        for(String id:Arrays.asList("viewer","editor","admin","root"))jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?,'hash',?,?,'before','before')",id,id,id,id.equals("root")?"SUPER_ADMIN":"USER");
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('a','Alpha','AA','before'),('b','Beta','BB','before')");jdbc.update("INSERT INTO user_team VALUES('viewer','a','VIEWER'),('editor','a','EDITOR'),('admin','a','TEAM_ADMIN')");
        for(String team:Arrays.asList("a","b"))jdbc.update("INSERT INTO api_definition(id,team_id,name,method,path,created_at,updated_at) VALUES(?,?,?,'POST','/path','before','before')","api-"+team,team,team);
        repository=new LogRepository(jdbc);users=new UserRepository();ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);TeamRepository teams=new TeamRepository();ReflectionTestUtils.setField(teams,"jdbcTemplate",jdbc);
        permission=new PermissionCheckerImpl();ReflectionTestUtils.setField(permission,"userRepository",users);ReflectionTestUtils.setField(permission,"teamRepository",teams);
        logs=new LogServiceImpl(repository,new LogRetainProperties(),permission);ReflectionTestUtils.setField(logs,"users",users);
        responses=new ApiResponseRepository(jdbc);apis=new ApiServiceImpl(new ApiRepository(jdbc),responses,new ApiTagRepository(jdbc),new GroupRepository(jdbc),new TagRepository(jdbc),mock(TeamService.class),permission,json,mock(LogService.class),users,new MockFileStorageService(temp.toString()));
        insert("log-a","a");insert("log-b","b");OperationLog operation=new OperationLog();operation.setId("operation");operation.setTeamId("a");operation.setUsername("Actor");operation.setAction("UPDATE");operation.setTargetType("API");operation.setTargetName("Route token=OP_NAME_SECRET");operation.setDetail("GET /path?secret=OP_DETAIL_SECRET&amount=99");operation.setCreatedAt("before");repository.insertOperationLog(operation);
        jwt=new JwtUtil();jwt.init();JwtAuthFilter filter=new JwtAuthFilter(jwt,json,new CurrentUserProviderImpl(users,new AuthSessionRepository(jdbc)));mvc=MockMvcBuilders.standaloneSetup(new LogController(logs)).setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();login("viewer");
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();}
    void insert(String id,String team){RequestLog log=new RequestLog();log.setId(id);log.setTeamId(team);log.setApiId("api-"+team);log.setApiPath("/path?access%5Ftoken=PATH_SECRET&amount=99");log.setMethod("POST");log.setRequestHeaders(headers);log.setRequestBody(body);log.setRequestParams("{\"access_token\":\"PARAM_SECRET\",\"amount\":\"99\"}");log.setDurationMs(25);log.setResponseCode(200);log.setCreatedAt("before");repository.insertRequestLog(log);}
    void login(String id){Map<String,Object>d=new HashMap<>();d.put("userId",id);d.put("username",id);d.put("globalRole",id.equals("root")?"SUPER_ADMIN":"USER");UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList());auth.setDetails(d);SecurityContextHolder.getContext().setAuthentication(auth);}
    String signed(String id){String sid=UUID.randomUUID().toString();jdbc.update("INSERT INTO auth_session(id,user_id,auth_version,created_at,expires_at) VALUES(?,?,0,'now',?)",sid,id,System.currentTimeMillis()+3600000);return jwt.generateToken(id,id,"SUPER_ADMIN",Arrays.asList("b"),0L,sid);}
    String http(String route,String id)throws Exception{return mvc.perform(get(route).header("Authorization","Bearer "+signed(id))).andReturn().getResponse().getContentAsString();}
    void noSecrets(String output){for(String secret:new String[]{"HEADER_SECRET","PROXY_SECRET","COOKIE_SECRET","SET_COOKIE_SECRET","XKEY_SECRET","APIKEY_SECRET","BODY_SECRET","NESTED_SECRET","PARAM_SECRET","PATH_SECRET","OP_NAME_SECRET","OP_DETAIL_SECRET"})assertFalse(output.contains(secret),secret+" leaked: "+output);}
    void condition(String source,String path,String expected,String name)throws Exception{
        jdbc.update("DELETE FROM api_response WHERE api_id='api-a'");Map<String,Object> c=new LinkedHashMap<>();c.put("source",source);c.put("path",path);c.put("operator","EQ");c.put("value",expected);c.put("valueType","STRING");Map<String,Object> rule=Collections.singletonMap("conditions",Collections.singletonList(c));
        jdbc.update("INSERT INTO api_response(id,api_id,name,conditions,is_active,sort_order,created_at,updated_at) VALUES('conditional','api-a',?,?,1,0,'before','before')",name,json.writeValueAsString(rule));jdbc.update("INSERT INTO api_response(id,api_id,name,is_active,sort_order,created_at,updated_at) VALUES('fallback','api-a','Fallback',1,1,'before','before')");
    }
    AiSettings settings(){AiSettings s=new AiSettings();s.setEnabled(true);s.setProvider("OPENAI_COMPATIBLE");s.setModel("test-model");return s;}
    AiExplainService explain(OpenAiCompatibleProvider provider){AiSettingsService policy=mock(AiSettingsService.class);when(policy.assertUsable(eq(AiSettingsService.CAP_EXPLAIN),anyString())).thenReturn(settings());return new AiExplainService(repository,apis,policy,provider,new AiMatchEvaluator(json),json);}
    @Test void viewerEditorListAndDetailMaskServerSideAndStoredSnapshotsAndCountsStayOriginal()throws Exception{
        for(String id:Arrays.asList("viewer","editor")){
            String list=http("/api/logs/request?teamId=a",id),detail=http("/api/logs/request/log-a",id);noSecrets(list);noSecrets(detail);JsonNode node=json.readTree(detail).path("data");assertTrue(node.path("redacted").asBoolean());assertEquals(1,json.readTree(list).path("data").path("total").asInt());assertEquals(25,node.path("durationMs").asLong());assertEquals(200,node.path("responseCode").asInt());assertTrue(node.path("requestBody").asText().contains("orderId"));assertTrue(node.path("requestBody").asText().contains("normal"));assertTrue(node.path("apiPath").asText().contains("amount=99"));noSecrets(http("/api/logs/operation?teamId=a",id));
        }
        assertEquals(headers,repository.findRequestLogById("log-a").getRequestHeaders());assertEquals(body,repository.findRequestLogById("log-a").getRequestBody());assertEquals(1,repository.countRequestLogs("a"));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM request_log",Integer.class));
    }
    @Test void teamAdminAndSuperMayReadFullWebLogAndPrivilegesAreLiveEvenForAnOldJwt()throws Exception{
        for(String id:Arrays.asList("admin","root")){String detail=http("/api/logs/request/log-a",id);assertTrue(detail.contains("HEADER_SECRET"));assertFalse(json.readTree(detail).path("data").path("redacted").asBoolean());}
        String old=signed("admin");jdbc.update("UPDATE user_team SET role='EDITOR' WHERE user_id='admin'");String result=mvc.perform(get("/api/logs/request/log-a").header("Authorization","Bearer "+old)).andReturn().getResponse().getContentAsString();noSecrets(result);jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='a'");jdbc.update("UPDATE user_team SET role='TEAM_ADMIN' WHERE user_id='admin'");assertTrue(http("/api/logs/request/log-a","admin").contains("HEADER_SECRET"));
    }
    @Test void listAndResourceResolvedDetailCannotCrossTeamsOrTrustForgedJwtRole()throws Exception{
        assertNotEquals(0,json.readTree(http("/api/logs/request?teamId=b","viewer")).path("code").asInt());assertNotEquals(0,json.readTree(http("/api/logs/request/log-b?teamId=a","viewer")).path("code").asInt());noSecrets(http("/api/logs/request/log-b","viewer"));login("viewer");assertThrows(BizException.class,()->explain(mock(OpenAiCompatibleProvider.class)).explain("log-b"));
    }
    @Test void malformedAndNonJsonSnapshotsAreOpaqueNotReturnedAsRawFallback()throws Exception{
        jdbc.update("UPDATE request_log SET request_headers='{invalid:BAD_HEADER}',request_body='password=BAD_BODY&token=BAD_TOKEN',request_params='%ZZ=BAD_QUERY' WHERE id='log-a'");String result=http("/api/logs/request/log-a","viewer");for(String secret:Arrays.asList("BAD_HEADER","BAD_BODY","BAD_TOKEN","BAD_QUERY"))assertFalse(result.contains(secret));assertTrue(result.contains("REDACTED_UNSTRUCTURED_BODY"));
    }
    @Test void privateHeaderDiagnosisIsUnknownButRealRuntimeHeaderMatchingIsUnchanged()throws Exception{
        condition("HEADER","Authorization","Bearer HEADER_SECRET","Private condition");OpenAiCompatibleProvider provider=mock(OpenAiCompatibleProvider.class);login("viewer");AiExplainResult result=explain(provider).explain("log-a");assertNull(result.getMatchedResponseId());assertTrue(result.getConclusion().contains("无法确定"));assertEquals("UNKNOWN",result.getChain().get(0).getStatus());assertNotEquals("FALLBACK_HIT",result.getChain().get(1).getStatus());noSecrets(json.writeValueAsString(result));verifyNoInteractions(provider);
        MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer HEADER_SECRET");ApiResponse actual=new ResponseMatcher(responses,json).match("api-a",request);assertEquals("conditional",actual.getId());assertEquals("Bearer HEADER_SECRET",request.getHeader("Authorization"));assertEquals(headers,repository.findRequestLogById("log-a").getRequestHeaders());
    }
    @Test void administratorAiPromptsAreAlwaysSanitizedAndProviderErrorsNeverFallBackToRawReasons()throws Exception{
        condition("QUERY","amount","0","token=RESPONSE_NAME_SECRET");OpenAiCompatibleProvider provider=mock(OpenAiCompatibleProvider.class);when(provider.complete(any(),anyString(),anyString())).thenReturn("{\"summary\":\"Safe explanation\",\"suggestion\":\"Safe suggestion\"}");login("root");AiExplainResult result=explain(provider).explain("log-a");org.mockito.ArgumentCaptor<String> prompt=org.mockito.ArgumentCaptor.forClass(String.class);verify(provider).complete(any(),anyString(),prompt.capture());noSecrets(prompt.getValue());assertFalse(prompt.getValue().contains("RESPONSE_NAME_SECRET"));assertTrue(prompt.getValue().contains("REDACTED"));assertNotNull(result.getMatchedResponseId());
        reset(provider);when(provider.complete(any(),anyString(),anyString())).thenThrow(new RuntimeException("HEADER_SECRET upstream error"));AiExplainResult fallback=explain(provider).explain("log-a");noSecrets(json.writeValueAsString(fallback));assertFalse(json.writeValueAsString(fallback).contains("RESPONSE_NAME_SECRET"));
    }
    @Test void opaqueBodyConditionIsUnknownNotAFakeMissOrFallback()throws Exception{
        condition("BODY","customer","expected","Body condition");jdbc.update("UPDATE request_log SET request_body='password=RAW_FORM_SECRET&customer=normal' WHERE id='log-a'");login("admin");OpenAiCompatibleProvider provider=mock(OpenAiCompatibleProvider.class);AiExplainResult result=explain(provider).explain("log-a");assertEquals("UNKNOWN",result.getChain().get(0).getStatus());assertNull(result.getMatchedResponseId());assertFalse(json.writeValueAsString(result).contains("RAW_FORM_SECRET"));verifyNoInteractions(provider);
    }
    @Test void actualProviderHttpBoundaryMasksChatLogHistoryEvenWhenTruncated()throws Exception{
        org.springframework.web.client.RestTemplate rest=new org.springframework.web.client.RestTemplate();org.springframework.test.web.client.MockRestServiceServer server=org.springframework.test.web.client.MockRestServiceServer.createServer(rest);
        final java.util.concurrent.atomic.AtomicReference<String> captured=new java.util.concurrent.atomic.AtomicReference<>();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://model.invalid/v1/chat/completions")).andExpect(request->{
            String payload=((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString();captured.set(payload);assertFalse(payload.contains("CHAT_SECRET"));assertFalse(payload.contains("LONG_SECRET"));assertEquals("Bearer PROVIDER_ONLY_SECRET",request.getHeaders().getFirst("Authorization"));
        }).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"choices\":[{\"message\":{\"content\":\"{\\\"intent\\\":\\\"HELP\\\",\\\"reply\\\":\\\"ok\\\"}\"}}]}",org.springframework.http.MediaType.APPLICATION_JSON));
        OpenAiCompatibleProvider provider=new OpenAiCompatibleProvider(json){@Override protected org.springframework.web.client.RestTemplate createRestTemplate(){return rest;}};
        AiChatService chat=new AiChatService(mock(AiSettingsService.class),provider,mock(MockAiGenerator.class),mock(AiService.class),apis,mock(StatsService.class),mock(PendingActionService.class),mock(AiConversationService.class),json);
        AiChatRequest request=new AiChatRequest();request.setTeamId("a");request.setMessage("分析请求 token=CHAT_SECRET，保留正常问题");AiChatMessage history=new AiChatMessage();history.setRole("user");StringBuilder secret=new StringBuilder();for(int i=0;i<100;i++)secret.append("LONG_SECRET ");history.setContent("日志：{\"password\":\""+secret+"\",\"orderId\":123}");request.setHistory(Collections.singletonList(history));
        AiSettings settings=settings();settings.setBaseUrl("http://model.invalid/v1");settings.setApiKey("PROVIDER_ONLY_SECRET");ReflectionTestUtils.invokeMethod(chat,"modelPlan",settings,request);server.verify();assertNotNull(captured.get());assertTrue(captured.get().contains("REDACTED"));
    }
    @Test void providerFailureDoesNotEchoItsConfiguredSecretOrCredentialFields(){
        org.springframework.web.client.RestTemplate rest=new org.springframework.web.client.RestTemplate();org.springframework.test.web.client.MockRestServiceServer server=org.springframework.test.web.client.MockRestServiceServer.createServer(rest);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://model.invalid/v1/chat/completions")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body("Incorrect API key: PROVIDER_ONLY_SECRET; token=ERROR_TOKEN_SECRET").contentType(org.springframework.http.MediaType.TEXT_PLAIN));
        OpenAiCompatibleProvider provider=new OpenAiCompatibleProvider(json){@Override protected org.springframework.web.client.RestTemplate createRestTemplate(){return rest;}};AiSettings settings=settings();settings.setBaseUrl("http://model.invalid/v1");settings.setApiKey("PROVIDER_ONLY_SECRET");BizException error=assertThrows(BizException.class,()->provider.complete(settings,"Safe","token=USER_SECRET"));assertFalse(error.getMessage().contains("PROVIDER_ONLY_SECRET"));assertFalse(error.getMessage().contains("ERROR_TOKEN_SECRET"));server.verify();
    }

}
