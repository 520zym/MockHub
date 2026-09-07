package com.mockhub.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.files.controller.FileDownloadController;
import com.mockhub.files.controller.FileManagementController;
import com.mockhub.files.model.StoredFile;
import com.mockhub.files.repository.FileRepository;
import com.mockhub.files.service.FilePreviewService;
import com.mockhub.files.service.FileServerService;
import com.mockhub.system.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileServerTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private FileRepository repo;
    private FileServerService service;
    private TeamRepository teams;
    private PermissionChecker permission;
    private MockMvc mvc;
    private final byte[] body = "0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @BeforeEach void setup() {
        SimpleDriverDataSource ds = new SimpleDriverDataSource(new org.sqlite.JDBC(), "jdbc:sqlite:" + temp.resolve("test.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('t1','Team1','T1','2026-01-01')");
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('t2','Team2','T2','2026-01-01')");
        teams = new TeamRepository(); ReflectionTestUtils.setField(teams, "jdbcTemplate", jdbc);
        permission = mock(PermissionChecker.class);
        repo = new FileRepository(jdbc);
        service = new FileServerService(repo, teams, permission, temp.toString(), 100);
        mvc = MockMvcBuilders.standaloneSetup(new FileDownloadController(service),
                new FileManagementController(service, new FilePreviewService(), new ObjectMapper(), "")).build();
        login("SUPER_ADMIN");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void login(String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("tester", "unused");
        Map<String,Object> details = new HashMap<>();
        details.put("userId", "u1"); details.put("globalRole", role);
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
    }
    private StoredFile upload(String name) {
        return service.uploadPublic("t1", new MockMultipartFile("file", name, "text/html", body));
    }

    @Test void publicUploadReturnsFileIdUrlAndDoesNotRequireLogin() throws Exception {
        SecurityContextHolder.clearContext();
        MvcResult result = mvc.perform(multipart("/file-server/T1/upload")
                .file(new MockMultipartFile("file", "../说明.txt", "text/html", body)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("data");
        String id = data.path("fileId").asText();
        assertTrue(data.path("downloadUrl").asText().endsWith("/files/" + id));
        assertEquals("说明.txt", data.path("fileName").asText());
        assertEquals(10, data.path("size").asInt());
        assertArrayEquals(body, Files.readAllBytes(service.path(id)));
        verifyNoInteractions(permission);
    }

    @Test void streamingRangesAndCountersCountOnlyWrittenBodyBytes() throws Exception {
        StoredFile file = upload("test.txt"); String url = "/files/" + file.getFileId();
        mvc.perform(head(url)).andExpect(status().isOk()).andExpect(header().string("Content-Length", "10"));
        assertEquals(0, repo.find(file.getFileId()).getDownloadCount());
        mvc.perform(get(url).header("Range", "bytes=2-5")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10")).andExpect(content().string("2345"));
        mvc.perform(get(url).header("Range", "bytes=-3")).andExpect(status().isPartialContent()).andExpect(content().string("789"));
        mvc.perform(get(url).header("Range", "bytes=8-")).andExpect(status().isPartialContent()).andExpect(content().string("89"));
        mvc.perform(get(url).header("Range", "bytes=10-")).andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */10"));
        mvc.perform(get(url).header("If-None-Match", "\"" + file.getFileId() + "\"")).andExpect(status().isNotModified());
        assertEquals(3, repo.find(file.getFileId()).getDownloadCount());
        assertEquals(9, repo.find(file.getFileId()).getTransferredBytes());
    }

    @Test void staleIfRangeReturnsFullFileAndMatchingIfRangeReturnsPartial() throws Exception {
        StoredFile file = upload("test.bin"); String url = "/files/" + file.getFileId();
        mvc.perform(get(url).header("Range", "bytes=0-1").header("If-Range", "\"stale\""))
                .andExpect(status().isOk()).andExpect(content().bytes(body));
        mvc.perform(get(url).header("Range", "bytes=0-1").header("If-Range", "\"" + file.getFileId() + "\""))
                .andExpect(status().isPartialContent()).andExpect(content().string("01"));
    }

    @Test void metadataSearchTagsAndTeamIsolation() {
        StoredFile file = upload("测试.txt");
        service.update(file.getFileId(), "测试别名", Arrays.asList("航图", "航图", "测试"));
        assertEquals(1, service.list(null, file.getFileId(), "航图", 1, 20).getTotal());
        assertEquals(1, service.list("t1", "别名", null, 1, 20).getTotal());
        assertEquals(2, service.detail(file.getFileId()).getTags().size());
        login("USER");
        assertEquals(0, service.list(null, null, null, 1, 20).getTotal());
        jdbc.update("INSERT INTO user_team(user_id,team_id) VALUES('u1','t1')");
        assertEquals(1, service.list(null, null, null, 1, 20).getTotal());
        verify(permission).checkTeamAdmin("t1");
    }

    @Test void batchDeleteChecksEveryTeamBeforeDeletingAnyFile() throws Exception {
        StoredFile a = upload("a.txt");
        StoredFile b = service.uploadPublic("T2", new MockMultipartFile("file", "b.txt", "text/plain", body));
        doThrow(new BizException(40101, "无权限")).when(permission).checkTeamAdmin("t2");
        assertThrows(BizException.class, () -> service.delete(Arrays.asList(a.getFileId(), b.getFileId())));
        assertNotNull(repo.find(a.getFileId())); assertTrue(Files.exists(service.path(a.getFileId())));
        reset(permission);
        service.delete(Arrays.asList(a.getFileId(), b.getFileId()));
        mvc.perform(get("/files/" + a.getFileId())).andExpect(status().isNotFound());
        mvc.perform(head("/files/" + b.getFileId())).andExpect(status().isNotFound());
        assertFalse(Files.exists(service.path(a.getFileId())));
    }

    @Test void rejectsTraversalAndOversizeWithoutLeakingFiles() throws Exception {
        assertThrows(BizException.class, () -> service.path("../test.db"));
        FileServerService tiny = new FileServerService(repo, teams, permission, temp.toString(), 5);
        assertThrows(BizException.class, () -> tiny.uploadPublic("T1", new MockMultipartFile("file", "a", "text/plain", body)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM file_server_file", Integer.class));
        mvc.perform(get("/files/not-a-file-id")).andExpect(status().isNotFound());
    }

    @Test void htmlNeverServedInlineAndPreviewDoesNotCountAsDownload() throws Exception {
        StoredFile file = upload("test.html");
        mvc.perform(get("/files/" + file.getFileId()).param("inline", "true"))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        assertEquals(1, repo.find(file.getFileId()).getDownloadCount());
        mvc.perform(get("/api/files/" + file.getFileId() + "/preview")).andExpect(status().isOk());
        assertEquals(1, repo.find(file.getFileId()).getDownloadCount());
    }

    @Test void emptyFileDownloadIsValidButRangeIsUnsatisfiable() throws Exception {
        StoredFile f = service.uploadPublic("T1", new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]));
        mvc.perform(get("/files/" + f.getFileId())).andExpect(status().isOk()).andExpect(header().string("Content-Length", "0"));
        mvc.perform(get("/files/" + f.getFileId()).header("Range", "bytes=0-"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }
}
