package com.mockhub.mcp.service;

import com.fasterxml.jackson.databind.*;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.common.filter.JwtAuthFilter;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.common.util.JwtUtil;
import com.mockhub.mcp.controller.PersonalMcpTokenController;
import com.mockhub.mcp.model.*;
import com.mockhub.mcp.repository.McpTokenRepository;
import com.mockhub.system.repository.*;
import com.mockhub.system.service.CurrentUserProviderImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonalMcpTokenIntegrationTest {
    @TempDir Path temp;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc; MockMvc mvc; JwtUtil jwt;
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup(){
        SQLiteDataSource ds=new SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+temp.resolve("personal.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);jdbc=new JdbcTemplate(ds);
        for(String id:Arrays.asList("owner","other","super"))jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?,'hash',?,?,'before','before')",id,id,id,id.equals("super")?"SUPER_ADMIN":"USER");
        for(String id:Arrays.asList("edit","view","hidden"))jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES(?,?,?,'before')",id,id,id);
        jdbc.update("INSERT INTO user_team VALUES('owner','edit','EDITOR'),('owner','view','VIEWER'),('other','edit','VIEWER')");
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->ds);context.registerBean(JdbcTemplate.class,()->jdbc);context.register(Config.class);context.refresh();jwt=context.getBean(JwtUtil.class);
        JwtAuthFilter filter=new JwtAuthFilter(jwt,json,new CurrentUserProviderImpl(context.getBean(UserRepository.class),context.getBean(AuthSessionRepository.class)));
        mvc=MockMvcBuilders.standaloneSetup(context.getBean(PersonalMcpTokenController.class)).setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();context.close();}
    String jwt(String id){String sid=UUID.randomUUID().toString();jdbc.update("INSERT INTO auth_session(id,user_id,auth_version,created_at,expires_at) VALUES(?,?,0,'now',?)",sid,id,System.currentTimeMillis()+3600000);return jwt.generateToken(id,id,"SUPER_ADMIN",Collections.singletonList("fake-team"),0L,sid);}
    JsonNode request(String id,Map<String,Object> body)throws Exception{return json.readTree(mvc.perform(post("/api/me/mcp-tokens").header("Authorization","Bearer "+jwt(id)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    Map<String,Object> body(List<String> teams,List<String> scopes){Map<String,Object>b=new LinkedHashMap<>();b.put("name","Desktop");b.put("expiresInDays",30);b.put("teamIds",teams);b.put("scopes",scopes);return b;}
    JsonNode mint(String id,String team,String scope)throws Exception{JsonNode result=request(id,body(Collections.singletonList(team),Collections.singletonList(scope)));assertEquals(0,result.path("code").asInt(),result.toString());return result.path("data");}
    @Test void rawSecretOnlyReturnedOnceAndOtherOwnersAreNeverListedOrRevoked()throws Exception{
        JsonNode own=mint("owner","edit","API_READ"),other=mint("other","edit","API_READ");String raw=own.path("token").asText();assertTrue(raw.startsWith("mhk_"));
        assertEquals(McpTokenService.sha256(raw),jdbc.queryForObject("SELECT token_hash FROM mcp_token WHERE id=?",String.class,own.path("id").asText()));
        String listed=mvc.perform(get("/api/me/mcp-tokens").header("Authorization","Bearer "+jwt("owner"))).andExpect(jsonPath("$.data.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertFalse(listed.contains(raw));assertFalse(listed.contains("tokenHash"));assertFalse(listed.contains(other.path("id").asText()));
        mvc.perform(delete("/api/me/mcp-tokens/"+other.path("id").asText()).header("Authorization","Bearer "+jwt("owner"))).andExpect(jsonPath("$.code").value(40405));
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id=?",String.class,other.path("id").asText()));
        mvc.perform(delete("/api/me/mcp-tokens/"+own.path("id").asText()).header("Authorization","Bearer "+jwt("owner"))).andExpect(jsonPath("$.code").value(0));
        assertNotNull(jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id=?",String.class,own.path("id").asText()));
    }
    @Test void viewerCannotMintWriteAndForgedJwtSuperSnapshotDoesNotAuthorize()throws Exception{
        assertEquals(40104,request("owner",body(Arrays.asList("view"),Arrays.asList("DRAFT_CREATE"))).path("code").asInt());
        assertEquals(40104,request("owner",body(Arrays.asList("hidden"),Arrays.asList("API_READ"))).path("code").asInt());
        assertEquals(0,request("owner",body(Arrays.asList("view"),Arrays.asList("API_READ","STATS_READ","AI_GENERATE"))).path("code").asInt());
    }
    @Test void perTeamScopeMatrixIsExactAndNeverCopiesEditorWritesToViewer()throws Exception{
        Map<String,Object>b=body(Arrays.asList("edit","view"),Arrays.asList("API_READ","DRAFT_CREATE"));
        Map<String,List<String>>matrix=new LinkedHashMap<>();matrix.put("edit",Arrays.asList("API_READ","DRAFT_CREATE"));matrix.put("view",Arrays.asList("API_READ"));b.put("teamScopes",matrix);
        JsonNode result=request("owner",b);assertEquals(0,result.path("code").asInt());JsonNode data=result.path("data");
        McpToken token=context.getBean(McpTokenRepository.class).findById(data.path("id").asText());assertFalse(McpScopeCodec.decode(token).get("view").contains(McpScope.DRAFT_CREATE));
        b.remove("teamScopes");assertEquals(40104,request("owner",b).path("code").asInt());
        matrix.put("view",Arrays.asList("DRAFT_CREATE"));b.put("teamScopes",matrix);assertEquals(40104,request("owner",b).path("code").asInt());
    }
    @Test void scopesUnknownDuplicateEmptyAndInvalidExpiryFailBeforeIssuance()throws Exception{
        List<Map<String,Object>>bad=new ArrayList<>();bad.add(body(Arrays.asList("edit","edit"),Arrays.asList("API_READ")));bad.add(body(Arrays.asList("edit"),Arrays.asList("API_READ","API_READ")));bad.add(body(Arrays.asList("edit"),Arrays.asList("SUPER_ADMIN")));bad.add(body(Collections.emptyList(),Arrays.asList("API_READ")));bad.add(body(Arrays.asList("edit"),Collections.emptyList()));
        for(int days:new int[]{0,-1,366}){Map<String,Object>b=body(Arrays.asList("edit"),Arrays.asList("API_READ"));b.put("expiresInDays",days);bad.add(b);}
        for(Map<String,Object>b:bad)assertNotEquals(0,request("owner",b).path("code").asInt());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mcp_token",Integer.class));
    }
    @Test void tokensWithoutV11ScopeOrWithCorruptOrExpiredRangeFailClosed()throws Exception{
        JsonNode issued=mint("owner","edit","API_READ");String id=issued.path("id").asText(),raw=issued.path("token").asText();McpTokenService service=context.getBean(McpTokenService.class);assertNotNull(service.authenticate(raw));
        jdbc.update("UPDATE mcp_token SET scope_version=0 WHERE id=?",id);assertNull(service.authenticate(raw));
        jdbc.update("UPDATE mcp_token SET scope_version=1,scopes='{}' WHERE id=?",id);assertNull(service.authenticate(raw));
        jdbc.update("UPDATE mcp_token SET scopes=?,expires_at=? WHERE id=?","{\"edit\":[\"API_READ\"]}",Instant.now().minusSeconds(1).toString(),id);assertNull(service.authenticate(raw));
    }
    @Test void liveOwnerCapabilitiesShrinkOnDemotionRemovalAndArchive()throws Exception{
        mint("super","hidden","DRAFT_CREATE");McpScopeService scopes=context.getBean(McpScopeService.class);
        assertTrue(scopes.ownerAllows("super","hidden",McpScope.DRAFT_CREATE));jdbc.update("UPDATE user SET global_role='USER' WHERE id='super'");assertFalse(scopes.ownerAllows("super","hidden",McpScope.DRAFT_CREATE));
        assertTrue(scopes.ownerAllows("owner","edit",McpScope.DRAFT_UPDATE));jdbc.update("UPDATE user_team SET role='VIEWER' WHERE user_id='owner' AND team_id='edit'");assertFalse(scopes.ownerAllows("owner","edit",McpScope.DRAFT_UPDATE));assertTrue(scopes.ownerAllows("owner","edit",McpScope.API_READ));
        jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='view'");assertTrue(scopes.ownerAllows("owner","view",McpScope.API_READ));assertFalse(scopes.ownerAllows("owner","view",McpScope.DRAFT_CREATE));
        jdbc.update("DELETE FROM user_team WHERE user_id='owner'");assertFalse(scopes.ownerAllows("owner","edit",McpScope.API_READ));
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({McpTokenService.class,McpScopeService.class,PersonalMcpTokenController.class,McpTokenRepository.class,UserRepository.class,TeamRepository.class,AuthSessionRepository.class,JwtUtil.class})
    static class Config{@Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}}
}
