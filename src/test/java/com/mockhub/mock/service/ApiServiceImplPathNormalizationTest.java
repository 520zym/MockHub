package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.ApiTagRepository;
import com.mockhub.mock.repository.GroupRepository;
import com.mockhub.mock.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiServiceImplPathNormalizationTest {

    private ApiRepository apiRepository;
    private ApiResponseRepository apiResponseRepository;
    private GroupRepository groupRepository;
    private PermissionChecker permissionChecker;
    private ApiServiceImpl service;

    @BeforeEach
    void setUp() {
        apiRepository = mock(ApiRepository.class);
        apiResponseRepository = mock(ApiResponseRepository.class);
        groupRepository = mock(GroupRepository.class);
        permissionChecker = mock(PermissionChecker.class);
        service = new ApiServiceImpl(
                apiRepository,
                apiResponseRepository,
                mock(ApiTagRepository.class),
                groupRepository,
                mock(TagRepository.class),
                mock(com.mockhub.system.service.TeamService.class),
                permissionChecker,
                new ObjectMapper(),
                mock(com.mockhub.log.service.LogService.class),
                mock(com.mockhub.system.repository.UserRepository.class),
                mock(MockFileStorageService.class)
        );
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", "user-1");
        details.put("username", "admin");
        details.put("globalRole", "SUPER_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void listFillsGroupNameFromRepository() {
        ApiDefinition api = new ApiDefinition();
        api.setId("api-1");
        api.setTeamId("team-1");
        api.setGroupId("group-1");
        api.setType("REST");
        api.setName("用户信息");
        api.setMethod("GET");
        api.setPath("/user/info");
        api.setResponseCode(200);
        api.setContentType("application/json");
        api.setCreatedAt("2026-06-21T20:00:00");
        api.setUpdatedAt("2026-06-21T20:00:00");

        ApiGroup group = new ApiGroup();
        group.setId("group-1");
        group.setTeamId("team-1");
        group.setName("用户模块");

        when(apiRepository.findAll(null, null, null, null, null, null,
                Collections.<String>emptyList(), null, null, null, 0, 20))
                .thenReturn(Collections.singletonList(api));
        when(apiRepository.count(null, null, null, null, null, null,
                Collections.<String>emptyList(), null))
                .thenReturn(1L);
        when(groupRepository.findById("group-1")).thenReturn(group);

        PageResult<ApiDefinitionVO> result = service.list(null, null, null, null,
                null, Collections.<String>emptyList(), null, null, null, 1, 20);

        assertEquals(1, result.getItems().size());
        assertEquals("用户模块", result.getItems().get(0).getGroupName());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAddsLeadingSlashBeforeConflictCheckAndPersist() {
        ApiDefinitionDTO dto = newDto("user/info");
        when(apiRepository.findByTeamIdAndPathAndMethod("team-1", "/user/info", "GET"))
                .thenReturn(Collections.<ApiDefinition>emptyList());

        service.create(dto);

        verify(apiRepository).findByTeamIdAndPathAndMethod("team-1", "/user/info", "GET");
        ArgumentCaptor<ApiDefinition> captor = ArgumentCaptor.forClass(ApiDefinition.class);
        verify(apiRepository).insert(captor.capture());
        assertEquals("/user/info", captor.getValue().getPath());
    }

    @Test
    void createRemovesQueryStringBeforeConflictCheckAndPersist() {
        ApiDefinitionDTO dto = newDto("/user/info?source=web");
        when(apiRepository.findByTeamIdAndPathAndMethod("team-1", "/user/info", "GET"))
                .thenReturn(Collections.<ApiDefinition>emptyList());

        service.create(dto);

        verify(apiRepository).findByTeamIdAndPathAndMethod("team-1", "/user/info", "GET");
        ArgumentCaptor<ApiDefinition> captor = ArgumentCaptor.forClass(ApiDefinition.class);
        verify(apiRepository).insert(captor.capture());
        assertEquals("/user/info", captor.getValue().getPath());
    }

    @Test
    void createPersistsRandomResponseModeAndSoapOperationMode() throws Exception {
        ApiDefinitionDTO dto = newDto("/random");
        dto.setResponseMode("RANDOM");
        java.util.Map<String, Object> operation = new java.util.HashMap<String, Object>();
        operation.put("operationName", "GetUser");
        operation.put("responseMode", "RANDOM");
        java.util.Map<String, Object> soapConfig = new java.util.HashMap<String, Object>();
        soapConfig.put("operations", Collections.<Object>singletonList(operation));
        dto.setSoapConfig(soapConfig);
        ApiResponseDTO restResponse = new ApiResponseDTO();
        restResponse.setActive(true);
        restResponse.setResponseBody("REST candidate");
        ApiResponseDTO soapResponse = new ApiResponseDTO();
        soapResponse.setActive(true);
        soapResponse.setSoapOperationName("GetUser");
        soapResponse.setResponseBody("SOAP candidate");
        dto.setResponses(java.util.Arrays.asList(restResponse, soapResponse));
        when(apiRepository.findByTeamIdAndPathAndMethod("team-1", "/random", "GET"))
                .thenReturn(Collections.<ApiDefinition>emptyList());

        service.create(dto);

        ArgumentCaptor<ApiDefinition> captor = ArgumentCaptor.forClass(ApiDefinition.class);
        verify(apiRepository).insert(captor.capture());
        assertEquals("RANDOM", captor.getValue().getResponseMode());
        assertTrue(captor.getValue().getSoapConfig().contains("\"responseMode\":\"RANDOM\""));
    }

    @Test
    void invalidSoapResponseModeDoesNotInsertApi() {
        ApiDefinitionDTO dto = newDto("/invalid-soap-mode");
        java.util.Map<String, Object> operation = new java.util.HashMap<String, Object>();
        operation.put("operationName", "GetUser");
        operation.put("responseMode", "ROUND_ROBIN");
        java.util.Map<String, Object> soapConfig = new java.util.HashMap<String, Object>();
        soapConfig.put("operations", Collections.<Object>singletonList(operation));
        dto.setSoapConfig(soapConfig);
        when(apiRepository.findByTeamIdAndPathAndMethod("team-1", "/invalid-soap-mode", "GET"))
                .thenReturn(Collections.<ApiDefinition>emptyList());

        com.mockhub.common.model.BizException ex = assertThrows(com.mockhub.common.model.BizException.class,
                () -> service.create(dto));

        assertEquals(40418, ex.getCode());
        verify(apiRepository, never()).insert(org.mockito.ArgumentMatchers.any(ApiDefinition.class));
    }

    @Test
    void randomModeWithoutResponsesDoesNotInsertApi() {
        ApiDefinitionDTO dto = newDto("/random-without-responses");
        dto.setResponseMode("RANDOM");
        when(apiRepository.findByTeamIdAndPathAndMethod("team-1", "/random-without-responses", "GET"))
                .thenReturn(Collections.<ApiDefinition>emptyList());

        com.mockhub.common.model.BizException ex = assertThrows(com.mockhub.common.model.BizException.class,
                () -> service.create(dto));

        assertEquals(40410, ex.getCode());
        verify(apiRepository, never()).insert(org.mockito.ArgumentMatchers.any(ApiDefinition.class));
    }

    @Test
    void switchingRandomToConditionWithoutFallbackDoesNotUpdateApi() {
        ApiDefinition existing = new ApiDefinition();
        existing.setId("api-random");
        existing.setTeamId("team-1");
        existing.setType("REST");
        existing.setName("随机接口");
        existing.setMethod("GET");
        existing.setPath("/random");
        existing.setResponseMode("RANDOM");
        when(apiRepository.findById("api-random")).thenReturn(existing);

        ApiDefinitionDTO dto = newDto("/random");
        dto.setResponseMode("CONDITION");
        com.mockhub.mock.model.dto.ApiResponseDTO first = new com.mockhub.mock.model.dto.ApiResponseDTO();
        first.setActive(true);
        first.setConditions("{\"conditions\":[{\"source\":\"QUERY\",\"path\":\"a\",\"operator\":\"EQ\",\"value\":\"1\",\"valueType\":\"STRING\"}]}");
        com.mockhub.mock.model.dto.ApiResponseDTO second = new com.mockhub.mock.model.dto.ApiResponseDTO();
        second.setActive(true);
        second.setConditions("{\"conditions\":[{\"source\":\"QUERY\",\"path\":\"b\",\"operator\":\"EQ\",\"value\":\"2\",\"valueType\":\"STRING\"}]}");
        dto.setResponses(java.util.Arrays.asList(first, second));

        com.mockhub.common.model.BizException ex = assertThrows(com.mockhub.common.model.BizException.class,
                () -> service.update("api-random", dto));

        assertEquals(40411, ex.getCode());
        verify(apiRepository, never()).update(org.mockito.ArgumentMatchers.any(ApiDefinition.class));
    }

    @Test
    void copyPreservesRandomResponseMode() {
        ApiDefinition source = new ApiDefinition();
        source.setId("source");
        source.setTeamId("team-1");
        source.setType("REST");
        source.setName("源接口");
        source.setMethod("GET");
        source.setPath("/source");
        source.setResponseMode("RANDOM");
        source.setCreatedAt("2026-01-01T00:00:00");
        source.setUpdatedAt("2026-01-01T00:00:00");
        when(apiRepository.findById("source")).thenReturn(source);
        when(apiResponseRepository.findByApiId("source")).thenReturn(Collections.<com.mockhub.mock.model.entity.ApiResponse>emptyList());

        service.copy("source");

        ArgumentCaptor<ApiDefinition> captor = ArgumentCaptor.forClass(ApiDefinition.class);
        verify(apiRepository).insert(captor.capture());
        assertEquals("RANDOM", captor.getValue().getResponseMode());
    }

    @Test
    void conflictCheckUsesNormalizedPath() {
        ApiDefinition existing = new ApiDefinition();
        existing.setId("api-1");
        existing.setName("用户信息");
        when(apiRepository.findByTeamIdAndPathAndMethod("team-1", "/user/info", "GET"))
                .thenReturn(Collections.singletonList(existing));

        String name = service.findConflictingApiName("team-1", "GET", "user/info?source=web", null);

        assertEquals("用户信息", name);
        verify(permissionChecker).checkTeamAccess("team-1");
        verify(apiRepository).findByTeamIdAndPathAndMethod(eq("team-1"), eq("/user/info"), eq("GET"));
    }

    private ApiDefinitionDTO newDto(String path) {
        ApiDefinitionDTO dto = new ApiDefinitionDTO();
        dto.setTeamId("team-1");
        dto.setType("REST");
        dto.setName("用户信息");
        dto.setMethod("GET");
        dto.setPath(path);
        dto.setResponseCode(200);
        dto.setContentType("application/json");
        dto.setResponseBody("{}");
        dto.setEnabled(true);
        return dto;
    }
}
