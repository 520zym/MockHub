package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.controller.ImportExportController;
import com.mockhub.mock.model.dto.*;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.repository.*;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.PermissionCheckerImpl;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real SQLite, real aggregate ZIP import and temporary files; no application server or real data. */
class MockFileRollbackIntegrationTest {
    @TempDir Path temp;
    Path data;
    SQLiteDataSource ds;
    JdbcTemplate jdbc;
    ApiRepository apis;
    ApiResponseRepository responses;
    ApiServiceImpl api;
    MockFileStorageService files;
    ImportExportService bundles;
    ImportExportController controller;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        data = temp.resolve("data");
        ds = new SQLiteDataSource(); ds.setUrl("jdbc:sqlite:" + temp.resolve("rollback.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        java.lang.reflect.Method migration = com.mockhub.common.config.DataSourceConfig.class.getDeclaredMethod("executeMigrations", DataSource.class);
        migration.setAccessible(true); migration.invoke(new com.mockhub.common.config.DataSourceConfig(), ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES('u','u','hash','User','USER','before','before')");
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('a','Source','AA','before'),('b','Target','BB','before'),('c','Other','CC','before')");
        jdbc.update("INSERT INTO user_team VALUES('u','a','TEAM_ADMIN'),('u','b','TEAM_ADMIN')");
        UserRepository users = new UserRepository(); ReflectionTestUtils.setField(users, "jdbcTemplate", jdbc);
        TeamRepository teams = new TeamRepository(); ReflectionTestUtils.setField(teams, "jdbcTemplate", jdbc);
        PermissionCheckerImpl permission = new PermissionCheckerImpl();
        ReflectionTestUtils.setField(permission, "userRepository", users); ReflectionTestUtils.setField(permission, "teamRepository", teams);
        apis = new ApiRepository(jdbc); responses = new ApiResponseRepository(jdbc);
        files = new MockFileStorageService(data.toString(), 1024, new MockBlobRepository(jdbc), jdbc);
        TeamService teamService = mock(TeamService.class);
        api = new ApiServiceImpl(apis, responses, new ApiTagRepository(jdbc), new GroupRepository(jdbc), new TagRepository(jdbc),
                teamService, permission, json, mock(LogService.class), users, files);
        bundles = new ImportExportService(apis, responses, new GroupRepository(jdbc), new TagRepository(jdbc), new ApiTagRepository(jdbc),
                new GlobalHeaderRepository(jdbc), teamService, permission, files, api);
        controller = new ImportExportController(bundles, json);
        Map<String,Object> details = new HashMap<>(); details.put("userId", "u"); details.put("username", "u"); details.put("globalRole", "USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", null, Collections.emptyList());
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); }

    @Test void validFileThenInvalidTagRollsBackDbAndOnlyFreshFiles() throws Exception {
        ApiDefinition first = create("a", "/existing", "source");
        ApiDefinition second = create("a", "/later", "second");
        ApiDefinition target = create("b", "/existing", "history");
        ApiDefinitionDTO current = dto("b", "/existing", store("b", "current")); current.setExpectedRevision(1L);
        api.update(target.getId(), current);
        store("c", "other-team-sentinel");
        ImportExportData pack = bundles.exportTeam("a");
        pack.setApis(Arrays.asList(apis.findById(first.getId()), apis.findById(second.getId())));
        pack.getApiTags().put(second.getId(), Collections.singletonList("undefined-tag"));
        byte[] archive = archive(pack);
        Map<String,Object> beforeDb = database(); Map<String,String> beforeFiles = disk();
        assertEquals(40000, assertThrows(BizException.class, () -> importZip(archive, "override", target.getId(), 2L)).getCode());
        assertEquals(beforeDb, database()); assertEquals(beforeFiles, disk());
        assertEquals(2, api.revisions(target.getId()).size());
    }

    @Test void laterHistoryFailureRestoresOldCurrentHistoryAndSourceFiles() throws Exception {
        ApiDefinition first = create("a", "/existing", "source");
        ApiDefinition second = create("a", "/later", "second");
        ApiDefinition target = create("b", "/existing", "old");
        byte[] archive = archive(ordered(first, second));
        Map<String,Object> beforeDb = database(); Map<String,String> beforeFiles = disk();
        jdbc.execute("CREATE TRIGGER reject_later_history BEFORE INSERT ON api_revision WHEN NEW.source='IMPORT' AND NEW.action='CREATE' BEGIN SELECT RAISE(ABORT,'history failed'); END");
        assertThrows(DataAccessException.class, () -> importZip(archive, "override", target.getId(), 1L));
        assertEquals(beforeDb, database()); assertEquals(beforeFiles, disk());
    }

    @Test void enclosingRollbackCleansImportedFilesButCommitKeepsFilesAndLeases() throws Exception {
        create("a", "/source", "source"); byte[] archive = archive(bundles.exportTeam("a"));
        Map<String,Object> beforeDb = database(); Map<String,String> beforeFiles = disk();
        transaction().execute(status -> {
            importZip(archive, "merge", null, null);
            assertEquals(beforeFiles.size() + 1, uncheckedDisk().size());
            status.setRollbackOnly(); return null;
        });
        assertEquals(beforeDb, database()); assertEquals(beforeFiles, disk());
        importZip(archive, "merge", null, null);
        String imported = responses.findByApiId(apis.findByTeamId("b").get(0).getId()).get(0).getFilePath();
        assertTrue(Files.isRegularFile(data.resolve("files").resolve(imported)));
        new MockBlobRepository(jdbc).requireUsable("b", imported);
        assertTrue(responses.findAllFilePaths().contains(imported));
        assertEquals(0, responses.withFileGcLock(() -> files.deleteOrphanFiles(responses.findAllFilePaths())).getDeletedCount());
    }

    @Test void nestedSavepointRollbackIsCompensatedBeforeOuterCommit() throws Exception {
        create("a", "/source", "source"); byte[] archive = archive(bundles.exportTeam("a"));
        Map<String,Object> beforeDb = database(); Map<String,String> beforeFiles = disk();
        transaction().execute(outer -> {
            TransactionTemplate nested = transaction(); nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
            nested.execute(inner -> { importZip(archive, "merge", null, null); inner.setRollbackOnly(); return null; });
            assertEquals(beforeDb, database()); return null;
        });
        assertEquals(beforeDb, database()); assertEquals(beforeFiles, disk());
    }

    @Test void ordinaryMultipartUploadRetainsLeaseAndIoFailureLeavesNeitherPartialFileNorLease() throws Exception {
        MockFileStorageService.StoredMockFile upload = files.store("a", new MockMultipartFile("file", "upload.bin", "application/octet-stream", new byte[]{1,2,3}));
        new MockBlobRepository(jdbc).requireUsable("a", upload.getFilePath());
        Map<String,Object> beforeDb = database(); Map<String,String> beforeFiles = disk();
        InputStream broken = new InputStream() {
            int reads;
            @Override public int read() throws IOException { if (reads++ == 3) throw new IOException("broken source"); return 7; }
        };
        assertThrows(BizException.class, () -> files.store("a", "broken.bin", "application/octet-stream", 8, broken));
        assertThrows(BizException.class, () -> files.store("a", "too-large.bin", "application/octet-stream", 1, new ByteArrayInputStream(new byte[1025])));
        assertEquals(beforeDb, database()); assertEquals(beforeFiles, disk());
        // Also preserve the old public low-level constructor's non-transactional upload contract.
        MockFileStorageService compatibility = new MockFileStorageService(data.toString(), 1024, new MockBlobRepository(jdbc));
        assertThrows(BizException.class, () -> compatibility.store("a", "broken.bin", "application/octet-stream", 1, new InputStream() {
            @Override public int read() throws IOException { throw new IOException("broken"); }
        }));
        assertEquals(beforeDb, database()); assertEquals(beforeFiles, disk());
    }

    @Test void gcCannotObservePhysicalFileBeforeItsUploadLeaseCommits() throws Exception {
        java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch gcInsideLock = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<MockFileStorageService.StoredMockFile> upload = pool.submit(() -> files.store("b", "racing.bin", "application/octet-stream", 1, new InputStream() {
                boolean done;
                @Override public int read() throws IOException {
                    if (done) return -1;
                    writing.countDown();
                    try { if (!finish.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("test timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                    done = true; return 1;
                }
            }));
            assertTrue(writing.await(5, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.Future<MockFileStorageService.OrphanCleanupResult> gc = pool.submit(() -> responses.withFileGcLock(() -> {
                gcInsideLock.countDown(); return files.deleteOrphanFiles(responses.findAllFilePaths());
            }));
            assertFalse(gcInsideLock.await(150, java.util.concurrent.TimeUnit.MILLISECONDS), "GC must wait for the upload writer lock");
            finish.countDown();
            String path = upload.get(5, java.util.concurrent.TimeUnit.SECONDS).getFilePath();
            assertEquals(0, gc.get(5, java.util.concurrent.TimeUnit.SECONDS).getDeletedCount());
            assertTrue(Files.isRegularFile(data.resolve("files").resolve(path)));
            new MockBlobRepository(jdbc).requireUsable("b", path);
        } finally { finish.countDown(); pool.shutdownNow(); }
    }

    @Test void rollbackDoesNotFollowAnotherTeamDirectoryOrReplacementFile() throws Exception {
        MockFileStorageService.StoredMockFile sentinel = store("c", "sentinel");
        Path team = data.resolve("files/b"); Files.createSymbolicLink(team, data.resolve("files/c"));
        Map<String,String> before = disk();
        assertThrows(BizException.class, () -> transaction().execute(status -> store("b", "unsafe")));
        assertEquals(before, disk()); assertTrue(Files.isRegularFile(data.resolve("files").resolve(sentinel.getFilePath())));
        Files.delete(team);
        Path replacement = temp.resolve("replacement.bin"); Files.write(replacement, new byte[]{9});
        transaction().execute(status -> {
            MockFileStorageService.StoredMockFile fresh = store("b", "fresh");
            try { Files.move(replacement, data.resolve("files").resolve(fresh.getFilePath()), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException e) { throw new UncheckedIOException(e); }
            status.setRollbackOnly(); return null;
        });
        assertTrue(disk().containsValue(Base64.getEncoder().encodeToString(new byte[]{9})), "rollback must not delete a replacement inode");
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(new DataSourceTransactionManager(ds)); }
    private MockFileStorageService.StoredMockFile store(String team, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return files.store(team, "payload.bin", "application/octet-stream", bytes.length, new ByteArrayInputStream(bytes));
    }
    private ApiDefinition create(String team, String path, String body) { return api.create(dto(team, path, store(team, body))); }
    private ApiDefinitionDTO dto(String team, String path, MockFileStorageService.StoredMockFile file) {
        ApiDefinitionDTO dto = new ApiDefinitionDTO(); dto.setTeamId(team); dto.setName(path); dto.setType("REST"); dto.setMethod("GET"); dto.setPath(path); dto.setEnabled(true);
        ApiResponseDTO r = new ApiResponseDTO(); r.setName("Default"); r.setActive(true); r.setResponseCode(200); r.setBodyType("FILE");
        r.setFilePath(file.getFilePath()); r.setFileName(file.getFileName()); r.setFileSize(file.getFileSize()); r.setContentType("application/octet-stream");
        dto.setResponses(Collections.singletonList(r)); return dto;
    }
    private ImportExportData ordered(ApiDefinition first, ApiDefinition second) {
        ImportExportData pack = bundles.exportTeam("a"); pack.setApis(Arrays.asList(first, second)); return pack;
    }
    private byte[] archive(ImportExportData pack) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("mockhub-export.json")); zip.write(json.writeValueAsBytes(pack)); zip.closeEntry();
            Set<String> added = new HashSet<>();
            for (com.mockhub.mock.model.entity.ApiResponse r : pack.getApiResponses()) {
                if (!"FILE".equals(r.getBodyType()) || !added.add(r.getFilePath())) continue;
                zip.putNextEntry(new ZipEntry("files/" + r.getFilePath())); zip.write(Files.readAllBytes(data.resolve("files").resolve(r.getFilePath()))); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private void importZip(byte[] bytes, String mode, String target, Long revision) {
        String expected = target == null ? null : "{\"" + target + "\":" + revision + "}";
        controller.importApis(new MockMultipartFile("file", "bundle.zip", "application/zip", bytes), "b", mode, expected);
    }
    private Map<String,Object> database() {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String table : Arrays.asList("api_definition", "api_response", "api_revision", "api_group", "tag", "api_tag", "operation_log", "mock_blob", "mock_blob_ref"))
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY rowid"));
        return result;
    }
    private Map<String,String> uncheckedDisk() { try { return disk(); } catch (IOException e) { throw new UncheckedIOException(e); } }
    private Map<String,String> disk() throws IOException {
        Map<String,String> result = new TreeMap<>(); Path root = data.resolve("files");
        if (Files.exists(root)) try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Iterator<Path> i = paths.iterator(); i.hasNext();) { Path p = i.next();
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) result.put(root.relativize(p).toString(), Base64.getEncoder().encodeToString(Files.readAllBytes(p)));
            }
        }
        return result;
    }
}
