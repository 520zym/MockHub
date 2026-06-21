package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.dto.ApiMatchResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockDispatchServiceFileResponseTest {

    private TeamService teamService;
    private ApiService apiService;
    private GlobalHeaderService globalHeaderService;
    private ResponseMatcher responseMatcher;
    private DynamicVariableResolver dynamicVariableResolver;
    private MockFileStorageService fileStorageService;
    private MockDispatchService service;

    @BeforeEach
    void setUp() {
        teamService = mock(TeamService.class);
        apiService = mock(ApiService.class);
        globalHeaderService = mock(GlobalHeaderService.class);
        responseMatcher = mock(ResponseMatcher.class);
        dynamicVariableResolver = mock(DynamicVariableResolver.class);
        fileStorageService = mock(MockFileStorageService.class);
        service = new MockDispatchService(
                teamService,
                apiService,
                mock(ApiResponseRepository.class),
                globalHeaderService,
                mock(com.mockhub.log.service.LogService.class),
                new ObjectMapper(),
                dynamicVariableResolver,
                responseMatcher,
                mock(SoapService.class),
                fileStorageService
        );
        when(globalHeaderService.buildHeaders(any(), any(), any()))
                .thenReturn(java.util.Collections.<String, String>emptyMap());
    }

    @Test
    void dispatchStreamsMatchedFileResponse() throws Exception {
        Team team = new Team();
        team.setId("team-1");
        team.setIdentifier("T1");
        when(teamService.findByIdentifier("T1")).thenReturn(team);

        ApiDefinition api = new ApiDefinition();
        api.setId("api-1");
        api.setName("下载报表");
        api.setPath("/download");
        ApiMatchResult match = new ApiMatchResult(api, java.util.Collections.<String, String>emptyMap());
        when(apiService.findMatch("team-1", "GET", "/download")).thenReturn(match);

        ApiResponse response = new ApiResponse();
        response.setId("resp-1");
        response.setApiId("api-1");
        response.setBodyType("FILE");
        response.setFilePath("team-1/api-1/resp-1/report.bin");
        response.setFileName("report.bin");
        response.setDownloadName("report.xlsx");
        response.setFileSize(4L);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setResponseCode(201);
        response.setActive(true);
        when(responseMatcher.match("api-1", new MockHttpServletRequest())).thenReturn(null);
        when(responseMatcher.match(eq("api-1"), any())).thenReturn(response);
        byte[] bytes = new byte[] {1, 2, 3, 4};
        when(fileStorageService.loadAsResource("team-1/api-1/resp-1/report.bin"))
                .thenReturn(new ByteArrayResource(bytes));

        ResponseEntity<?> entity = service.dispatch("T1", "GET", "/download", new MockHttpServletRequest());

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                entity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("4", entity.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH));
        assertTrue(entity.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("report.xlsx"));
        assertArrayEquals(bytes, ((ByteArrayResource) entity.getBody()).getByteArray());
        verify(dynamicVariableResolver, never()).resolve(any(), any(), any(), any());
    }
}
