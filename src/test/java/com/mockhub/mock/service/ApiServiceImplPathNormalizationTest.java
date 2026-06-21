package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.ApiTagRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiServiceImplPathNormalizationTest {

    private ApiRepository apiRepository;
    private PermissionChecker permissionChecker;
    private ApiServiceImpl service;

    @BeforeEach
    void setUp() {
        apiRepository = mock(ApiRepository.class);
        permissionChecker = mock(PermissionChecker.class);
        service = new ApiServiceImpl(
                apiRepository,
                mock(ApiResponseRepository.class),
                mock(ApiTagRepository.class),
                mock(TagRepository.class),
                mock(com.mockhub.system.service.TeamService.class),
                permissionChecker,
                new ObjectMapper(),
                mock(com.mockhub.log.service.LogService.class),
                mock(com.mockhub.system.repository.UserRepository.class)
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
