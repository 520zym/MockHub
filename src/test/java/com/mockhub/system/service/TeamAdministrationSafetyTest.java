package com.mockhub.system.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.system.model.dto.*;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real file SQLite and Spring transactional proxies, never mock the locking/counting path. */
class TeamAdministrationSafetyTest {
    @TempDir Path temp;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TeamServiceImpl teams;
    @BeforeEach void setup() {
        SQLiteConfig config = new SQLiteConfig(); config.setBusyTimeout(10000);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + temp.resolve("safety.db"));
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("PRAGMA journal_mode=WAL");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> ds);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.register(Config.class); context.refresh();
        teams = context.getBean(TeamServiceImpl.class);
        for (String id : Arrays.asList("root", "a", "b", "viewer", "editor", "outside", "disabled")) user(id, id.equals("root") ? "SUPER_ADMIN" : "USER");
        jdbc.update("UPDATE user SET status='DISABLED' WHERE id='disabled'");
        team("t1", "T1"); team("t2", "T2"); team("orphan", "ORPHAN");
        member("a", "t1", "TEAM_ADMIN"); member("b", "t2", "TEAM_ADMIN");
        member("viewer", "t1", "VIEWER"); member("editor", "t1", "EDITOR");
        login("root", "SUPER_ADMIN");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    void user(String id, String role) {
        jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?, 'hash',?,?, 'before','before')", id,id,id,role);
    }
    void team(String id, String identifier) {
        jdbc.update("INSERT INTO team(id,name,identifier,created_at,created_by) VALUES(?,?,?,'before','a')",id,id,identifier);
    }
    void member(String id, String team, String role) { jdbc.update("INSERT INTO user_team VALUES(?,?,?)",id,team,role); }
    static void login(String id, String role) {
        Map<String,Object> details = new HashMap<>(); details.put("userId",id); details.put("username",id); details.put("globalRole",role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList());
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
    }
    String role(String id, String team) { return jdbc.queryForObject("SELECT role FROM user_team WHERE user_id=? AND team_id=?",String.class,id,team); }
    AddMemberRequest add(String id, String role) { AddMemberRequest r=new AddMemberRequest();r.setUserId(id);r.setRole(role);return r; }
    UpdateMemberRoleRequest change(String role) { UpdateMemberRoleRequest r=new UpdateMemberRoleRequest();r.setRole(role);return r; }
    UpdateTeamStatusRequest status(String status) { UpdateTeamStatusRequest r=new UpdateTeamStatusRequest();r.setStatus(status);return r; }
    @Test void safetyLockRequiresRealTransaction() {
        assertTrue(AopUtils.isCglibProxy(teams));
        assertThrows(IllegalStateException.class, () -> context.getBean(AdminSafetyService.class).acquireWriteLock());
    }
    @Test void viewerEditorCanReadButCannotManageEvenWithLegacyGlobalTeamAdmin() {
        for (String id : Arrays.asList("viewer","editor")) {
            login(id,"TEAM_ADMIN");
            assertEquals(3,teams.listMembers("t1").size());
            assertThrows(BizException.class, () -> teams.addMember("t1",add("outside","VIEWER")));
            assertThrows(BizException.class, () -> teams.removeMember("t1","a"));
            assertThrows(BizException.class, () -> teams.updateMemberRole("t1","a",change("EDITOR")));
            assertThrows(BizException.class, () -> teams.update("t1",new UpdateTeamRequest()));
            assertThrows(BizException.class, () -> teams.memberCandidates("t1","outside"));
            assertThrows(BizException.class, () -> teams.listMembers("t2"));
        }
        login("outside","USER"); assertThrows(BizException.class, () -> teams.listMembers("t1"));
    }
    @Test void teamAdminCanManageOnlyOwnTeamAndCannotArchiveOrCreate() {
        login("a","USER");
        teams.addMember("t1",add("outside","MEMBER")); assertEquals("EDITOR",role("outside","t1"));
        teams.updateMemberRole("t1","outside",change("VIEWER")); assertEquals("VIEWER",role("outside","t1"));
        teams.removeMember("t1","outside");
        UpdateTeamRequest r = new UpdateTeamRequest();r.setName("new name");r.setDescription("description");r.setColor("#fff");
        assertEquals("description",teams.update("t1",r).getDescription());
        assertThrows(BizException.class, () -> teams.addMember("t2",add("outside","EDITOR")));
        assertThrows(BizException.class, () -> teams.update("t2",r));
        assertThrows(BizException.class, () -> teams.create(new CreateTeamRequest()));
        assertThrows(BizException.class, () -> teams.updateStatus("t1",status("ARCHIVED")));
        assertThrows(BizException.class, () -> teams.delete("t1"));
    }
    @Test void candidatesAreExactActiveMinimalAndNotAlreadyMembers() throws Exception {
        login("a","USER");
        assertEquals(1,teams.memberCandidates("t1","outside").size());
        assertTrue(teams.memberCandidates("t1","out").isEmpty());
        assertTrue(teams.memberCandidates("t1","%").isEmpty());
        assertTrue(teams.memberCandidates("t1","OUTSIDE").isEmpty());
        assertTrue(teams.memberCandidates("t1","disabled").isEmpty());
        assertTrue(teams.memberCandidates("t1","a").isEmpty());
        assertThrows(BizException.class, () -> teams.memberCandidates("t1",""));
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(teams.memberCandidates("t1","root").get(0));
        assertFalse(json.contains("globalRole")); assertFalse(json.contains("password"));assertFalse(json.contains("status"));
    }
    @Test void everyRoleInputRejectsUnknownAndMissingMembersOrDisabledCandidates() {
        for(String bad : Arrays.asList("SUPER_ADMIN","UNKNOWN","",null)) {
            assertThrows(BizException.class, () -> teams.addMember("t1",add("outside",bad)));
            assertThrows(BizException.class, () -> teams.updateMemberRole("t1","a",change(bad)));
        }
        assertThrows(BizException.class, () -> teams.addMember("t1",add("disabled","TEAM_ADMIN")));
        assertThrows(BizException.class, () -> teams.addMember("t1",add("missing","EDITOR")));
        assertThrows(BizException.class, () -> teams.addMember("t1",add("a","EDITOR")));
        assertThrows(BizException.class, () -> teams.updateMemberRole("t1","outside",change("EDITOR")));
    }
    @Test void lastAdminCannotBeRemovedDemotedOrLeaveAndSuperDoesNotCount() {
        member("root","t1","EDITOR"); member("disabled","t1","TEAM_ADMIN");
        assertThrows(BizException.class, () -> teams.removeMember("t1","a")); assertEquals("TEAM_ADMIN",role("a","t1"));
        assertThrows(BizException.class, () -> teams.updateMemberRole("t1","a",change("EDITOR"))); assertEquals("TEAM_ADMIN",role("a","t1"));
        login("a","USER"); assertThrows(BizException.class, () -> teams.leave("t1"));assertEquals("TEAM_ADMIN",role("a","t1"));
        login("root","SUPER_ADMIN"); assertThrows(BizException.class, () -> teams.leave("t2"));
    }
    @Test void nonLastAdminCanLeaveAndCreatorAndOtherMembershipsArePreserved() {
        member("b","t1","TEAM_ADMIN");
        login("a","USER"); teams.leave("t1");
        assertEquals("a",teams.getById("t1").getCreatedBy());
        assertEquals("TEAM_ADMIN",role("b","t2")); assertEquals("TEAM_ADMIN",role("b","t1"));
    }
    @Test void unrelatedOrphansAreReportedAndDoNotBlockHealthyTeamChanges() {
        Team orphan = teams.listAll().stream().filter(t -> t.getId().equals("orphan")).findFirst().get();
        assertTrue(orphan.getAdminRepairRequired());assertEquals(0,orphan.getEffectiveAdminCount());
        teams.addMember("t1",add("outside","TEAM_ADMIN"));teams.removeMember("t1","a");
        assertEquals("TEAM_ADMIN",role("outside","t1"));
        teams.addMember("orphan",add("a","TEAM_ADMIN"));
        assertEquals(1,context.getBean(AdminSafetyService.class).effectiveTeamAdminCount("orphan"));
    }
    @Test void createRequiresActiveInitialAdminAndImmutableValidIdentifier() {
        CreateTeamRequest r = new CreateTeamRequest();r.setName("new");r.setIdentifier("NEW");
        assertThrows(BizException.class, () -> teams.create(r));
        r.setInitialAdminUserId("disabled");assertThrows(BizException.class, () -> teams.create(r));
        r.setInitialAdminUserId("a");r.setIdentifier("lower");assertThrows(BizException.class, () -> teams.create(r));
        r.setIdentifier("NEW");Team t=teams.create(r);assertEquals("TEAM_ADMIN",role("a",t.getId()));
        UpdateTeamRequest update = new UpdateTeamRequest();update.setIdentifier("RENAME");
        assertThrows(BizException.class, () -> teams.update(t.getId(),update));assertEquals("NEW",teams.getById(t.getId()).getIdentifier());
        r.setName("another");assertThrows(BizException.class, () -> teams.create(r));
    }
    @Test void creationAndInitialMembershipAreAtomicOnInsertFailure() {
        jdbc.execute("CREATE TRIGGER deny_members BEFORE INSERT ON user_team BEGIN SELECT RAISE(ABORT,'no membership'); END");
        CreateTeamRequest r=new CreateTeamRequest();r.setName("new");r.setIdentifier("NEW");r.setInitialAdminUserId("a");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> teams.create(r));
        assertNull(teams.findByIdentifier("NEW"));
    }
    @Test void archiveFreezesManagementWithoutChangingMockResourcesAndIdentifierRemainsReserved() {
        jdbc.update("INSERT INTO api_definition(id,team_id,name,method,path,created_at,updated_at,created_by) VALUES('api','t1','API','GET','/path','before','before','a')");
        teams.updateStatus("t1",status("ARCHIVED")); assertTrue(teams.getById("t1").isArchived());
        assertEquals(1,jdbc.queryForObject("SELECT enabled FROM api_definition WHERE id='api'",Integer.class));
        assertEquals("a",jdbc.queryForObject("SELECT created_by FROM api_definition WHERE id='api'",String.class));
        assertEquals("t1",teams.findByIdentifier("T1").getId());
        assertThrows(BizException.class, () -> teams.addMember("t1",add("outside","EDITOR")));
        assertThrows(BizException.class, () -> teams.removeMember("t1","viewer"));
        assertThrows(BizException.class, () -> teams.update("t1",new UpdateTeamRequest()));
        assertThrows(BizException.class, () -> teams.updateMemberRole("t1","viewer",change("EDITOR")));
        login("viewer","USER"); assertEquals(3,teams.listMembers("t1").size());assertThrows(BizException.class, () -> teams.leave("t1"));
        login("root","SUPER_ADMIN");teams.updateStatus("t1",status("ACTIVE"));teams.addMember("t1",add("outside","EDITOR"));
        assertThrows(BizException.class, () -> teams.delete("t1"));
        assertEquals(40403,assertThrows(BizException.class, () -> teams.delete("missing")).getCode());
    }
    @Test void simultaneousTwoAdminLeavesSerializeAndExactlyOneCommits() throws Exception {
        member("b","t1","TEAM_ADMIN");
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(String id:Arrays.asList("a","b")) futures.add(executor.submit(() -> {
                login(id,"USER");ready.countDown();start.await();
                try { teams.leave("t1");return true; }
                catch(BizException expected) { assertEquals(40901,expected.getCode());return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            int committed=0;for(Future<Boolean> f:futures) if(f.get(15,TimeUnit.SECONDS)) committed++;
            assertEquals(1,committed);
            assertEquals(1,context.getBean(AdminSafetyService.class).effectiveTeamAdminCount("t1"));
        } finally { start.countDown();executor.shutdownNow(); }
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({TeamServiceImpl.class,TeamRepository.class,UserRepository.class,AdminSafetyService.class,AdminSafetyRepository.class,PermissionCheckerImpl.class})
    static class Config {
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
}
