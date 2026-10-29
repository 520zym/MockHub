package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.PageResult;
import com.mockhub.mock.model.dto.*;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.repository.*;
import com.mockhub.log.service.LogService;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.PermissionCheckerImpl;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real SQLite aggregate, real authorization, true transaction/CAS; never starts the application. */
class ApiLifecycleIntegrationTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private ApiRepository apis;
    private ApiResponseRepository responses;
    private ApiServiceImpl service;
    private final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setUp() throws Exception {
        SQLiteDataSource ds=new SQLiteDataSource(); ds.setUrl("jdbc:sqlite:"+temp.resolve("lifecycle.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc=new JdbcTemplate(ds);
        java.lang.reflect.Method migrate=com.mockhub.common.config.DataSourceConfig.class.getDeclaredMethod("executeMigrations",javax.sql.DataSource.class);
        migrate.setAccessible(true);migrate.invoke(new com.mockhub.common.config.DataSourceConfig(),ds);
        for(String user:new String[]{"u","other"}) jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) "
                + "VALUES(?,?, 'hash',?,'USER','before','before')",user,user,"Name "+user);
        for(String team:new String[]{"a","b"}) jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES(?,?,?,'before')",team,team,team);
        jdbc.update("INSERT INTO user_team VALUES('u','a','TEAM_ADMIN'),('other','a','EDITOR')");
        jdbc.update("INSERT INTO tag(id,team_id,name,color) VALUES('tag','a','Tag','#fff')");
        UserRepository users=new UserRepository(); ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);
        PermissionCheckerImpl permission=new PermissionCheckerImpl(); ReflectionTestUtils.setField(permission,"userRepository",users);
        apis=new ApiRepository(jdbc);responses=new ApiResponseRepository(jdbc);
        service=new ApiServiceImpl(apis,responses,new ApiTagRepository(jdbc),new GroupRepository(jdbc),new TagRepository(jdbc),
                mock(TeamService.class),permission,json,mock(LogService.class),users,new MockFileStorageService(temp.toString()));
        signIn("u");
    }
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }

    @Test void snapshotsContainWholeConfigurationButNeverIdentityDeletionOrRuntimeState() throws Exception {
        ApiDefinitionDTO dto=dto("/aggregate","old");
        Map<String,Object> operation=new HashMap<String,Object>();operation.put("operationName","GetUser");
        dto.setSoapConfig(Collections.singletonMap("operations",Collections.singletonList(operation)));
        ApiDefinition api=service.create(dto);
        assertEquals(1,api.getRevision());
        assertEquals(1,service.revisions(api.getId()).size());
        Map<String,Object> version=service.revisions(api.getId()).get(0);
        JsonNode snapshot=json.valueToTree(service.revision(api.getId(),version.get("id").toString()).get("snapshot"));
        assertEquals("old",snapshot.path("config").path("response_body").asText());
        assertTrue(snapshot.path("config").path("soap_config").asText().contains("GetUser"));
        assertEquals("old",snapshot.path("responses").get(0).path("response_body").asText());
        assertEquals("tag",snapshot.path("tags").get(0).path("id").asText());
        for(String forbidden:new String[]{"team_id","created_by","hit_count","last_called_at","deleted_at","revision"}) {
            assertFalse(snapshot.path("config").has(forbidden),forbidden);
        }
        assertEquals("Name u",version.get("actorNameSnapshot"));
        apis.incrementHitCount(api.getId(),"called");
        assertEquals(1,apis.findById(api.getId()).getRevision());
        assertEquals(1,service.revisions(api.getId()).size());
    }

    @Test void publishedSnapshotCannotBeUpdatedAndVersionReadsDoNotLeakOtherApis() {
        ApiDefinition one=service.create(dto("/one","one")),two=service.create(dto("/two","two"));
        String version=service.revisions(one.getId()).get(0).get("id").toString();
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE api_revision SET snapshot_json='{}' WHERE id=?",version));
        assertThrows(BizException.class,()->service.revision(two.getId(),version));
    }

    @Test void failedHistoryOrAuditWriteRollsBackMainResponsesTagsVersionAndAudit() {
        ApiDefinition api=service.create(dto("/atomic","old"));
        jdbc.execute("CREATE TRIGGER reject_revision BEFORE INSERT ON api_revision WHEN NEW.action='UPDATE' "
                + "BEGIN SELECT RAISE(ABORT,'history failed'); END");
        ApiDefinitionDTO update=dto("/atomic","new"); update.setTagIds(Collections.emptyList());
        assertThrows(DataAccessException.class,()->service.update(api.getId(),update));
        assertEquals("old",apis.findById(api.getId()).getResponseBody());
        assertEquals("old",responses.findByApiId(api.getId()).get(0).getResponseBody());
        assertEquals(1,apis.findById(api.getId()).getRevision());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM api_tag WHERE api_id=?",Integer.class,api.getId()));
        assertEquals(1,service.revisions(api.getId()).size());
        jdbc.execute("DROP TRIGGER reject_revision");
        jdbc.execute("CREATE TRIGGER reject_audit BEFORE INSERT ON operation_log WHEN NEW.action='UPDATE' "
                + "BEGIN SELECT RAISE(ABORT,'audit failed'); END");
        assertThrows(DataAccessException.class,()->service.update(api.getId(),update));
        assertEquals(1,apis.findById(api.getId()).getRevision()); assertEquals(1,service.revisions(api.getId()).size());
    }

    @Test void twoClientsWithSameRevisionCannotOverwriteEachOther() throws Exception {
        ApiDefinition api=service.create(dto("/cas","old"));
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            List<Future<String>> results=new ArrayList<Future<String>>();
            for(String value:new String[]{"one","two"}) results.add(pool.submit(()->{
                signIn("u");ready.countDown();go.await();
                try { service.update(api.getId(),dto("/cas",value));return "OK"; }
                catch(BizException e) { assertEquals(40921,e.getCode());return "CONFLICT"; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
            List<String> outcomes=Arrays.asList(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(outcomes,"OK"));assertEquals(1,Collections.frequency(outcomes,"CONFLICT"));
            assertEquals(2,apis.findById(api.getId()).getRevision());assertEquals(2,service.revisions(api.getId()).size());
        } finally { go.countDown();pool.shutdownNow(); }
    }

    @Test void softDeleteStopsMockAndReleasesPathButKeepsAggregateAndHistory() {
        ApiDefinition api=service.create(dto("/reuse","old"));
        assertNotNull(service.findMatch("a","GET","/reuse"));
        service.delete(api.getId(),1L);
        assertNull(service.findMatch("a","GET","/reuse"));assertNull(apis.findById(api.getId()));
        assertThrows(BizException.class,()->service.getById(api.getId()));
        assertEquals("old",responses.findByApiId(api.getId()).get(0).getResponseBody());
        assertEquals(2,service.revisions(api.getId()).size());
        assertNotEquals(api.getId(),service.create(dto("/reuse","replacement")).getId());
    }

    @Test void historyAndWritesUseCurrentTeamRolesNotCreatorOrHistoricalRole() {
        ApiDefinition api=service.create(dto("/roles","old"));
        jdbc.update("UPDATE user_team SET role='VIEWER' WHERE user_id='u'");
        assertEquals(1,service.revisions(api.getId()).size());
        assertEquals(40104,assertThrows(BizException.class,()->service.update(api.getId(),dto("/roles","bad"))).getCode());
        jdbc.update("DELETE FROM user_team WHERE user_id='u'");
        assertEquals(40102,assertThrows(BizException.class,()->service.revisions(api.getId())).getCode());
        signIn("other");assertEquals(2,service.update(api.getId(),dto("/roles","editor")).getRevision());
    }

    @Test void batchValidatesAllExpectedVersionsBeforeChangingAnyAggregate() {
        ApiDefinition one=service.create(dto("/one","one")),two=service.create(dto("/two","two"));
        Map<String,Long> expected=new HashMap<String,Long>();expected.put(one.getId(),1L);expected.put(two.getId(),2L);
        assertEquals(40921,assertThrows(BizException.class,()->service.batch("disable",Arrays.asList(one.getId(),two.getId()),null,expected)).getCode());
        assertEquals(1,apis.findById(one.getId()).getRevision());assertTrue(apis.findById(one.getId()).isEnabled());
        expected.put(two.getId(),1L);service.batch("disable",Arrays.asList(one.getId(),two.getId()),null,expected);
        assertEquals(2,apis.findById(one.getId()).getRevision());assertEquals(2,apis.findById(two.getId()).getRevision());
        assertFalse(apis.findById(one.getId()).isEnabled());assertEquals(2,service.revisions(two.getId()).size());
    }

    @Test void creatorFilterUsesSqlForRowsAndTotalAfterSoftDelete() {
        service.create(dto("/mine-one","one"));ApiDefinition deleted=service.create(dto("/mine-two","two"));
        signIn("other");service.create(dto("/others","other"));signIn("u");service.delete(deleted.getId(),1L);
        PageResult<ApiDefinitionVO> mine=service.list("a",null,null,null,null,null,null,null,null,1,1,"me");
        assertEquals(1,mine.getTotal());assertEquals(1,mine.getItems().size());
        assertEquals("u",mine.getItems().get(0).getCreatedBy());assertEquals(1,mine.getItems().get(0).getRevision());
        assertEquals("Name u",mine.getItems().get(0).getUpdatedByName());
    }
    private ApiDefinitionDTO dto(String path,String body) {
        ApiDefinitionDTO dto=new ApiDefinitionDTO();dto.setTeamId("a");dto.setName(path);dto.setMethod("GET");dto.setPath(path);
        dto.setEnabled(true);dto.setResponseBody(body);dto.setExpectedRevision(1L);dto.setTagIds(Collections.singletonList("tag"));
        ApiResponseDTO response=new ApiResponseDTO();response.setName("Default");response.setActive(true);response.setResponseBody(body);
        dto.setResponses(Collections.singletonList(response));return dto;
    }
    private void signIn(String user) {
        Map<String,Object> details=new HashMap<String,Object>();details.put("userId",user);details.put("username",user);details.put("globalRole","USER");
        UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(user,null,Collections.emptyList());
        auth.setDetails(details);SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
