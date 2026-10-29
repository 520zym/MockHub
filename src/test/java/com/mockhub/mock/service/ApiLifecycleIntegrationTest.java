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
        com.mockhub.system.repository.TeamRepository teams=new com.mockhub.system.repository.TeamRepository();
        ReflectionTestUtils.setField(teams,"jdbcTemplate",jdbc); ReflectionTestUtils.setField(permission,"teamRepository",teams);
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
        assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM api_revision WHERE id=?",version));
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
    @Test void restoreDetectsReusedPathAndRestoresOnlyToDisabledStateWithMissingReferencesRemoved() {
        jdbc.update("INSERT INTO api_group(id,team_id,name,created_at) VALUES('g','a','Group','before')");
        ApiDefinitionDTO original=dto("/reuse","old");original.setGroupId("g");
        ApiDefinition api=service.create(original);service.delete(api.getId(),1L);
        service.create(dto("/reuse","replacement"));
        assertEquals(40922,assertThrows(BizException.class,()->service.restore(api.getId(),2L,null)).getCode());
        jdbc.update("DELETE FROM api_group WHERE id='g'");jdbc.update("DELETE FROM tag WHERE id='tag'");
        ApiDefinition restored=service.restore(api.getId(),2L,"restored?ignore=query");
        assertEquals("/restored",restored.getPath());assertFalse(restored.isEnabled());assertNull(restored.getDeletedAt());
        assertNull(restored.getGroupId());assertEquals(3,restored.getRevision());
        assertTrue(service.getById(api.getId()).getTags().isEmpty());
        assertNull(service.findMatch("a","GET","/restored"));assertEquals(3,service.revisions(api.getId()).size());
    }

    @Test void trashCapabilitiesAreScopedAndExpiredRowsCannotBeRestoredBeforeCleanup() {
        ApiDefinition api=service.create(dto("/trash","old"));service.delete(api.getId(),1L);
        jdbc.update("UPDATE user_team SET role='VIEWER' WHERE user_id='u'");
        Map<String,Object> item=service.trash("a",1,20).getItems().get(0);
        assertEquals(false,item.get("canRestore"));assertEquals(false,item.get("canPurge"));
        assertEquals(40104,assertThrows(BizException.class,()->service.restore(api.getId(),2L,null)).getCode());
        jdbc.update("UPDATE user_team SET role='EDITOR' WHERE user_id='u'");
        assertEquals(true,service.trash("a",1,20).getItems().get(0).get("canRestore"));
        assertEquals(40101,assertThrows(BizException.class,()->service.purge(api.getId(),2L)).getCode());
        jdbc.update("UPDATE api_definition SET purge_after='2000-01-01T00:00:00Z' WHERE id=?",api.getId());
        assertEquals(40923,assertThrows(BizException.class,()->service.restore(api.getId(),2L,null)).getCode());
        assertEquals(false,service.trash("a",1,20).getItems().get(0).get("canRestore"));
        assertEquals(1,apis.purgeExpired(100));assertEquals(0,apis.purgeExpired(100));
        assertNull(apis.findIncludingDeleted(api.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM api_tombstone WHERE api_id=?",Integer.class,api.getId()));
    }

    @Test void purgeErasesAllContentSnapshotsButLeavesContentFreeAuditAndCanRollBackOnFailure() {
        ApiDefinition api=service.create(dto("/purge","secret-body"));service.delete(api.getId(),1L);
        jdbc.execute("CREATE TRIGGER reject_purge BEFORE DELETE ON api_definition BEGIN SELECT RAISE(ABORT,'purge failed'); END");
        assertThrows(DataAccessException.class,()->service.purge(api.getId(),2L));
        assertNotNull(apis.findIncludingDeleted(api.getId()));assertEquals(2,service.revisions(api.getId()).size());
        assertEquals(1,responses.findByApiId(api.getId()).size());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM api_tombstone",Integer.class));
        jdbc.execute("DROP TRIGGER reject_purge");service.purge(api.getId(),2L);
        assertNull(apis.findIncludingDeleted(api.getId()));assertTrue(responses.findByApiId(api.getId()).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM api_revision WHERE api_id=?",Integer.class,api.getId()));
        assertThrows(BizException.class,()->service.revisions(api.getId()));
        assertFalse(jdbc.queryForMap("SELECT * FROM api_tombstone WHERE api_id=?",api.getId()).toString().contains("secret-body"));
        assertEquals(0,jdbc.queryForObject("SELECT history_delete_allowed FROM api_lifecycle_lock WHERE id=1",Integer.class));
    }

    @Test void rollbackRestoresSoapRulesBodyAndFilesWithoutRestoringCreatorCountersOrDeletedState() throws Exception {
        MockFileStorageService storage=new MockFileStorageService(temp.toString(),10485760L,new MockBlobRepository(jdbc));
        MockFileStorageService.StoredMockFile file=storage.store("a","example.txt","text/plain",3,new java.io.ByteArrayInputStream(new byte[]{1,2,3}));
        ApiDefinitionDTO original=dto("/rollback","old");original.setType("SOAP");
        original.setSoapConfig(Collections.singletonMap("operations",Collections.singletonList(Collections.singletonMap("operationName","GetOld"))));
        ApiResponseDTO soap=new ApiResponseDTO();soap.setSoapOperationName("GetOld");soap.setName("SOAP");soap.setActive(true);soap.setResponseBody("<old/>");
        ApiResponseDTO attachment=new ApiResponseDTO();attachment.setBodyType("FILE");attachment.setFilePath(file.getFilePath());
        attachment.setFileName("example.txt");attachment.setFileSize(3L);attachment.setName("Attachment");
        ApiResponseDTO conditional=new ApiResponseDTO();conditional.setName("Rule");conditional.setActive(true);conditional.setResponseBody("conditional-old");
        conditional.setConditions("{\"conditions\":[{\"source\":\"QUERY\",\"path\":\"kind\",\"operator\":\"EQ\",\"value\":\"old\",\"valueType\":\"STRING\"}]}");
        original.setResponses(Arrays.asList(conditional,original.getResponses().get(0),soap,attachment));
        ApiDefinition api=service.create(original);String version=service.revisions(api.getId()).get(0).get("id").toString();
        apis.incrementHitCount(api.getId(),"latest-hit");signIn("other");service.update(api.getId(),dto("/changed","new"));
        assertTrue(responses.findAllFilePaths().contains(file.getFilePath()),"history must protect replaced file");
        assertEquals(0,storage.deleteOrphanFiles(responses.findAllFilePaths()).getDeletedCount());
        ApiDefinition rolled=service.rollback(api.getId(),version,2L);
        assertEquals(3,rolled.getRevision());assertEquals("/rollback",rolled.getPath());assertEquals("old",rolled.getResponseBody());
        assertEquals("SOAP",rolled.getType());assertTrue(rolled.getSoapConfig().contains("GetOld"));
        assertEquals("u",rolled.getCreatedBy());assertEquals("other",rolled.getUpdatedBy());
        assertEquals(1,rolled.getHitCount());assertEquals("latest-hit",rolled.getLastCalledAt());assertNull(rolled.getDeletedAt());
        assertEquals(4,responses.findByApiId(api.getId()).size());
        assertTrue(responses.findByApiId(api.getId()).stream().anyMatch(r -> r.getConditions()!=null && r.getConditions().contains("kind")));
        assertEquals(version,service.revisions(api.getId()).get(0).get("restoredFromRevisionId"));
        service.delete(api.getId(),3L);
        assertEquals(40926,assertThrows(BizException.class,()->service.rollback(api.getId(),version,4L)).getCode());
    }

    @Test void configurableRetentionPrunesOnlyActiveHistoryAndKeepsTrashSnapshots() {
        ReflectionTestUtils.setField(service,"apiRepository",new ApiRepository(jdbc,2));
        ApiDefinition api=service.create(dto("/retention","one"));service.update(api.getId(),dto("/retention","two"));
        ApiDefinitionDTO third=dto("/retention","three");third.setExpectedRevision(2L);service.update(api.getId(),third);
        assertEquals(2,service.revisions(api.getId()).size());
        service.delete(api.getId(),3L);assertEquals(3,service.revisions(api.getId()).size());
        assertEquals(0,jdbc.queryForObject("SELECT history_delete_allowed FROM api_lifecycle_lock WHERE id=1",Integer.class));
        service.restore(api.getId(),4L,null);assertEquals(2,service.revisions(api.getId()).size());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='REVISION_PRUNE'",Integer.class)>0);
    }

    @Test void concurrentRestoreAndPurgeHaveExactlyOneWinnerAndNeverLoseAnActiveAggregate() throws Exception {
        ApiDefinition api=service.create(dto("/race","old"));service.delete(api.getId(),1L);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {
            Future<String> restore=pool.submit(()->{signIn("u");go.await();try { service.restore(api.getId(),2L,null);return "restored"; }
                catch(BizException e) { return "rejected"; } finally { SecurityContextHolder.clearContext(); }});
            Future<String> purge=pool.submit(()->{signIn("u");go.await();try { service.purge(api.getId(),2L);return "purged"; }
                catch(BizException e) { return "rejected"; } finally { SecurityContextHolder.clearContext(); }});
            go.countDown();String restored=restore.get(10,TimeUnit.SECONDS),purged=purge.get(10,TimeUnit.SECONDS);
            assertNotEquals("rejected",restored.equals("rejected")?purged:restored);
            assertFalse(restored.equals("restored")&&purged.equals("purged"));
            if(restored.equals("restored")) { assertNotNull(apis.findById(api.getId()));assertFalse(apis.findById(api.getId()).isEnabled()); }
            else assertNull(apis.findIncludingDeleted(api.getId()));
        } finally { go.countDown();pool.shutdownNow(); }
    }

    @Test void attachmentsRequireSameTeamLiveLeaseOrRetainedReferenceAndGcProtectsThem() throws Exception {
        MockBlobRepository blobs=new MockBlobRepository(jdbc);
        MockFileStorageService storage=new MockFileStorageService(temp.toString(),10485760L,blobs);
        MockFileStorageService.StoredMockFile foreign=storage.store("b","foreign.txt","text/plain",1,new java.io.ByteArrayInputStream(new byte[]{1}));
        ApiDefinitionDTO malicious=dto("/foreign","body");ApiResponseDTO response=malicious.getResponses().get(0);
        response.setBodyType("FILE");response.setFilePath(foreign.getFilePath());response.setFileName("foreign.txt");response.setFileSize(1L);
        assertEquals(40925,assertThrows(BizException.class,()->service.create(malicious)).getCode());
        assertEquals(0,apis.countByTeamId("a"));
        MockFileStorageService.StoredMockFile own=storage.store("a","own.txt","text/plain",1,new java.io.ByteArrayInputStream(new byte[]{2}));
        response.setFilePath(own.getFilePath());response.setFileName("own.txt");
        ApiDefinition api=service.create(malicious);
        jdbc.update("UPDATE mock_blob SET lease_expires_at=0 WHERE file_path=?",own.getFilePath());
        blobs.requireUsable("a",own.getFilePath());
        service.update(api.getId(),dto("/foreign","text-now"));
        assertTrue(responses.findAllFilePaths().contains(own.getFilePath()));
        assertEquals(0,storage.deleteOrphanFiles(responses.findAllFilePaths()).getDeletedCount());
        service.delete(api.getId(),2L);service.purge(api.getId(),3L);
        assertEquals(40925,assertThrows(BizException.class,()->blobs.requireUsable("a",own.getFilePath())).getCode());
        assertEquals(1,storage.deleteOrphanFiles(responses.findAllFilePaths()).getDeletedCount());
        assertNotNull(storage.loadAsResource(foreign.getFilePath()),"another team's valid upload lease must survive GC");
    }

    @Test void expiredUnclaimedUploadLeaseIsRejectedAndCanBeGarbageCollected() throws Exception {
        MockBlobRepository blobs=new MockBlobRepository(jdbc);MockFileStorageService storage=new MockFileStorageService(temp.toString(),10485760L,blobs);
        MockFileStorageService.StoredMockFile file=storage.store("a","temporary.txt","text/plain",1,new java.io.ByteArrayInputStream(new byte[]{1}));
        assertTrue(responses.findAllFilePaths().contains(file.getFilePath()));
        jdbc.update("UPDATE mock_blob SET lease_expires_at=0 WHERE file_path=?",file.getFilePath());
        assertThrows(BizException.class,()->blobs.requireUsable("a",file.getFilePath()));
        MockFileMaintenanceService maintenance=new MockFileMaintenanceService(responses,storage,new com.mockhub.common.config.MockFileProperties());
        assertEquals(1,maintenance.cleanOrphanFiles().getDeletedCount());
    }

    @Test void groupAndTagMutationsVersionEveryAffectedAggregateIncludingTrashAndRollBackOnHistoryFailure() {
        GroupService groups=new GroupService(new GroupRepository(jdbc),apis,permission());
        TagService tags=new TagService(new TagRepository(jdbc),new ApiTagRepository(jdbc),permission(),apis);
        com.mockhub.mock.model.entity.ApiGroup group=new com.mockhub.mock.model.entity.ApiGroup();group.setTeamId("a");group.setName("Old group");groups.create(group);
        ApiDefinitionDTO dto=dto("/metadata","body");dto.setGroupId(group.getId());ApiDefinition api=service.create(dto);
        group.setName("New group");groups.update(group.getId(),group);assertEquals(2,apis.findById(api.getId()).getRevision());
        com.mockhub.mock.model.entity.Tag tag=new com.mockhub.mock.model.entity.Tag();tag.setName("New tag");tag.setColor("#abc");
        jdbc.execute("CREATE TRIGGER reject_metadata_history BEFORE INSERT ON api_revision WHEN NEW.action='TAG_UPDATE' "
                + "BEGIN SELECT RAISE(ABORT,'metadata history failed'); END");
        assertThrows(DataAccessException.class,()->tags.update("tag",tag));
        assertEquals("Tag",jdbc.queryForObject("SELECT name FROM tag WHERE id='tag'",String.class));
        assertEquals(2,apis.findById(api.getId()).getRevision());jdbc.execute("DROP TRIGGER reject_metadata_history");
        tags.update("tag",tag);service.delete(api.getId(),3L);groups.delete(group.getId());tags.delete("tag");
        assertEquals(6,apis.findIncludingDeleted(api.getId()).getRevision());assertEquals(6,service.revisions(api.getId()).size());
        ApiDefinition restored=service.restore(api.getId(),6L,null);assertNull(restored.getGroupId());assertTrue(service.getById(api.getId()).getTags().isEmpty());
    }
    private PermissionCheckerImpl permission() {
        UserRepository users=new UserRepository();ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);
        PermissionCheckerImpl permission=new PermissionCheckerImpl();ReflectionTestUtils.setField(permission,"userRepository",users);return permission;
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
