package com.mockhub.system.service;

import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.auth.service.CredentialRevocationService;
import com.mockhub.common.model.BizException;
import com.mockhub.mcp.repository.McpTokenRepository;
import com.mockhub.system.model.dto.*;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class UserAdministrationSafetyTest {
    @TempDir Path temp;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc; UserServiceImpl users;
    @BeforeEach void setup() {
        SQLiteConfig config=new SQLiteConfig();config.setBusyTimeout(10000);
        SQLiteDataSource ds=new SQLiteDataSource(config);ds.setUrl("jdbc:sqlite:"+temp.resolve("users.db"));
        jdbc=new JdbcTemplate(ds);jdbc.execute("PRAGMA journal_mode=WAL");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->ds);context.registerBean(JdbcTemplate.class,()->jdbc);
        context.register(Config.class);context.refresh();users=context.getBean(UserServiceImpl.class);
        user("root","SUPER_ADMIN","ACTIVE");user("a","USER","ACTIVE");user("b","USER","ACTIVE");
        user("disabled","SUPER_ADMIN","DISABLED");user("outside","USER","ACTIVE");
        for(String id:Arrays.asList("t1","t2","orphan")) jdbc.update("INSERT INTO team(id,name,identifier,created_at,created_by) VALUES(?,?,?,'before','a')",id,id,id.toUpperCase());
        member("a","t1","TEAM_ADMIN");member("b","t2","TEAM_ADMIN");
        seedCredentials("a");seedCredentials("root");login("root","SUPER_ADMIN");
    }
    @AfterEach void close() { org.springframework.security.core.context.SecurityContextHolder.clearContext();context.close(); }
    void user(String id,String role,String status) { jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,status,created_at,updated_at) VALUES(?,?,'hash',?,?,?,'before','before')",id,id,id,role,status); }
    void member(String id,String team,String role) { jdbc.update("INSERT INTO user_team VALUES(?,?,?)",id,team,role); }
    void seedCredentials(String id) {
        jdbc.update("INSERT INTO auth_session(id,user_id,auth_version,created_at,expires_at) VALUES(?,?,0,'before',9999999999999)","web-"+id,id);
        jdbc.update("INSERT INTO mcp_token(id,name,token_hash,token_prefix,user_id,created_at) VALUES(?,'MCP',?,'prefix',?,'before')","mcp-"+id,"hash-"+id,id);
    }
    static void login(String id,String role) { TeamAdministrationSafetyTest.login(id,role); }
    User user(String id) { return context.getBean(UserRepository.class).findById(id); }
    UpdateUserStatusRequest status(String s) { UpdateUserStatusRequest r=new UpdateUserStatusRequest();r.setStatus(s);return r; }
    UpdateUserRequest update(String role) { UpdateUserRequest r=new UpdateUserRequest();r.setGlobalRole(role);return r; }
    AssignTeamsRequest assign(String... pairs) {
        AssignTeamsRequest r=new AssignTeamsRequest();List<AssignTeamsRequest.TeamRoleItem> items=new ArrayList<>();
        for(int i=0;i<pairs.length;i+=2) { AssignTeamsRequest.TeamRoleItem item=new AssignTeamsRequest.TeamRoleItem();item.setTeamId(pairs[i]);item.setRole(pairs[i+1]);items.add(item); }
        r.setTeamRoles(items);return r;
    }
    String role(String id,String team) { return context.getBean(UserRepository.class).findUserTeamRole(id,team); }
    void assertCredentials(String id,boolean revoked,long version) {
        assertEquals(version,user(id).getAuthVersion());
        assertEquals(revoked,jdbc.queryForObject("SELECT revoked_at FROM auth_session WHERE id=?",String.class,"web-"+id)!=null);
        assertEquals(revoked,jdbc.queryForObject("SELECT revoked_at FROM mcp_token WHERE id=?",String.class,"mcp-"+id)!=null);
    }
    @Test void platformAccountManagementCannotBeDelegatedToAnyTeamRole() {
        assertTrue(AopUtils.isCglibProxy(users));
        for(String teamRole:Arrays.asList("VIEWER","EDITOR","TEAM_ADMIN")) {
            jdbc.update("UPDATE user_team SET role=? WHERE user_id='a' AND team_id='t1'",teamRole);login("a","USER");
            assertThrows(BizException.class,()->users.create(new CreateUserRequest()));
            assertThrows(BizException.class,()->users.update("outside",update("SUPER_ADMIN")));
            assertThrows(BizException.class,()->users.delete("outside"));
            assertThrows(BizException.class,()->users.assignTeams("outside",assign("t2","TEAM_ADMIN")));
            assertThrows(BizException.class,()->users.updateStatus("outside",status("DISABLED")));
        }
    }
    @Test void lastTeamAdminDisableDeleteAndBatchReplacementRollbackEverything() {
        assertThrows(BizException.class,()->users.updateStatus("a",status("DISABLED")));
        assertEquals("ACTIVE",user("a").getStatus());assertCredentials("a",false,0);
        assertThrows(BizException.class,()->users.assignTeams("a",assign("t2","VIEWER")));
        assertEquals("TEAM_ADMIN",role("a","t1"));assertNull(role("a","t2"));
        assertThrows(BizException.class,()->users.assignTeams("a",assign("t1","EDITOR")));
        assertEquals("TEAM_ADMIN",role("a","t1"));
        assertThrows(BizException.class,()->users.assignTeams("a",assign()));assertEquals("TEAM_ADMIN",role("a","t1"));
        assertThrows(BizException.class,()->users.delete("a"));assertNotNull(user("a"));assertEquals("TEAM_ADMIN",role("a","t1"));assertCredentials("a",false,0);
    }
    @Test void disabledAdminAndGlobalSuperMembershipDoNotReplaceRealActiveTeamAdmin() {
        member("disabled","t1","TEAM_ADMIN");member("root","t1","EDITOR");
        assertThrows(BizException.class,()->users.updateStatus("a",status("DISABLED")));
        assertEquals("ACTIVE",user("a").getStatus());
    }
    @Test void lastActiveSuperCannotBeDisabledDeletedOrDemotedEvenWithDisabledSuperPresent() {
        assertThrows(BizException.class,()->users.updateStatus("root",status("DISABLED")));assertCredentials("root",false,0);
        assertThrows(BizException.class,()->users.update("root",update("USER")));assertEquals("SUPER_ADMIN",user("root").getGlobalRole());
        assertThrows(BizException.class,()->users.delete("root"));assertNotNull(user("root"));assertCredentials("root",false,0);
    }
    @Test void disabledThenRestoredAccountNeverRevivesWebOrMcpCredentials() {
        member("b","t1","TEAM_ADMIN");
        assertEquals("DISABLED",users.updateStatus("a",status("DISABLED")).getStatus());assertCredentials("a",true,1);
        assertEquals("ACTIVE",users.updateStatus("a",status("ACTIVE")).getStatus());assertCredentials("a",true,2);
        users.updateStatus("a",status("ACTIVE"));assertCredentials("a",true,3);
        assertEquals("TEAM_ADMIN",role("a","t1"));
    }
    @Test void revocationFailureRollsBackStatusVersionAndBothCredentialKinds() {
        member("b","t1","TEAM_ADMIN");
        jdbc.execute("CREATE TRIGGER fail_mcp BEFORE UPDATE OF revoked_at ON mcp_token BEGIN SELECT RAISE(ABORT,'fail revoke'); END");
        assertThrows(org.springframework.dao.DataAccessException.class,()->users.updateStatus("a",status("DISABLED")));
        assertEquals("ACTIVE",user("a").getStatus());assertCredentials("a",false,0);
    }
    @Test void batchRejectsUnknownDuplicateMissingAndArchivedTeamBeforeChangingAnything() {
        for(AssignTeamsRequest invalid:Arrays.asList(assign("t1","TEAM_ADMIN","t1","VIEWER"),assign("t1","SUPER_ADMIN"),assign("missing","VIEWER"),assign("t1",null))) {
            assertThrows(BizException.class,()->users.assignTeams("a",invalid));assertEquals("TEAM_ADMIN",role("a","t1"));
        }
        jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='t1'");
        assertThrows(BizException.class,()->users.assignTeams("a",assign("t2","EDITOR")));
        users.assignTeams("a",assign("t1","TEAM_ADMIN","t2","MEMBER"));
        assertEquals("TEAM_ADMIN",role("a","t1"));assertEquals("EDITOR",role("a","t2"));
        assertThrows(BizException.class,()->users.assignTeams("disabled",assign("t2","TEAM_ADMIN")));
    }
    @Test void successfulBatchMultiTeamMembershipPreservesCreatorAndGlobalRole() {
        member("b","t1","TEAM_ADMIN");users.assignTeams("a",assign("t1","VIEWER","t2","EDITOR"));
        assertEquals("VIEWER",role("a","t1"));assertEquals("EDITOR",role("a","t2"));assertEquals("USER",user("a").getGlobalRole());
        assertEquals("a",jdbc.queryForObject("SELECT created_by FROM team WHERE id='t1'",String.class));
        assertTrue(context.getBean(TeamRepository.class).findByUserId("a").size()==2);
    }
    @Test void legacyGlobalRolesNormalizeToUserNeverToSuperAndUnknownRejects() {
        for(String role:Arrays.asList("MEMBER","TEAM_ADMIN")) {
            CreateUserRequest r=new CreateUserRequest();r.setUsername("new-"+role);r.setPassword("password");r.setGlobalRole(role);
            User created=users.create(r);assertEquals("USER",created.getGlobalRole());
            assertEquals("USER",users.update("outside",update(role)).getGlobalRole());
        }
        assertThrows(BizException.class,()->users.update("outside",update("EDITOR")));
        CreateUserRequest bad=new CreateUserRequest();bad.setUsername("bad");bad.setPassword("password");bad.setGlobalRole("ADMIN");
        assertThrows(BizException.class,()->users.create(bad));
        assertNull(context.getBean(UserRepository.class).findByUsername("bad"));
    }
    @Test void physicalDeleteCleansReferencesWithForeignKeysOffAndPreservesResourcesAndAudit() {
        assertEquals(0,jdbc.queryForObject("PRAGMA foreign_keys",Integer.class));
        member("b","t1","TEAM_ADMIN");
        jdbc.update("INSERT INTO api_definition(id,team_id,name,method,path,created_at,updated_at,created_by,updated_by) VALUES('api','t1','API','GET','/path','before','before','a','a')");
        jdbc.update("INSERT INTO operation_log(id,user_id,username,action,target_type,created_at) VALUES('audit','a','a','TEST','USER','before')");
        users.delete("a");assertNull(user("a"));assertNull(role("a","t1"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM auth_session WHERE user_id='a'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mcp_token WHERE user_id='a'",Integer.class));
        assertNull(jdbc.queryForObject("SELECT created_by FROM api_definition WHERE id='api'",String.class));
        assertNull(jdbc.queryForObject("SELECT updated_by FROM api_definition WHERE id='api'",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT enabled FROM api_definition WHERE id='api'",Integer.class));
        assertEquals("a",jdbc.queryForObject("SELECT user_id FROM operation_log WHERE id='audit'",String.class));
    }
    @Test void nonLastSuperCanBeDemotedOrDeletedWithoutAffectingItsTeamRole() {
        user("root2","SUPER_ADMIN","ACTIVE");
        member("root2","t1","VIEWER");
        users.update("root2",update("TEAM_ADMIN"));assertEquals("USER",user("root2").getGlobalRole());assertEquals("VIEWER",role("root2","t1"));
        user("root3","SUPER_ADMIN","ACTIVE");users.delete("root3");assertNull(user("root3"));assertNotNull(user("root"));
    }
    @Test void unknownStatusOrMissingUserCannotSucceed() {
        for(String s:Arrays.asList("LOCKED","",null)) assertThrows(BizException.class,()->users.updateStatus("a",status(s)));
        assertThrows(BizException.class,()->users.updateStatus("missing",status("DISABLED")));
        assertEquals("ACTIVE",user("a").getStatus());assertCredentials("a",false,0);
    }
    @Test void concurrentDisablesOfTwoAdminsLeaveOneEffectiveAdmin() throws Exception {
        member("b","t1","TEAM_ADMIN");
        // b's own t2 would independently reject disable; give t2 another active admin to test t1 contention.
        member("outside","t2","TEAM_ADMIN");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(String id:Arrays.asList("a","b")) futures.add(pool.submit(()-> {login("root","SUPER_ADMIN");ready.countDown();start.await();try {users.updateStatus(id,status("DISABLED"));return true;}catch(BizException ex){assertEquals(40901,ex.getCode());return false;}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();int committed=0;for(Future<Boolean> f:futures) if(f.get(15,TimeUnit.SECONDS))committed++;
            assertEquals(1,committed);assertEquals(1,context.getBean(AdminSafetyService.class).effectiveTeamAdminCount("t1"));
        } finally {start.countDown();pool.shutdownNow();}
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({UserServiceImpl.class,UserRepository.class,TeamRepository.class,AdminSafetyService.class,AdminSafetyRepository.class,CredentialRevocationService.class,AuthSessionRepository.class,McpTokenRepository.class})
    static class Config { @Bean PlatformTransactionManager transactionManager(DataSource ds) {return new DataSourceTransactionManager(ds);} }
}
