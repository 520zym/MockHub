package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.ai.model.dto.AiApiFullUpdateDraft;
import com.mockhub.ai.model.dto.AiApiUpdateDraft;
import com.mockhub.ai.repository.AiPendingActionRepository;
import com.mockhub.ai.service.PendingActionService;
import com.mockhub.common.model.BizException;
import com.mockhub.files.repository.FileRepository;
import com.mockhub.files.service.FileServerService;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ImportExportData;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.repository.*;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.PermissionCheckerImpl;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DomainReadOnlyGuardTest {
    @TempDir Path temp;
    private UserRepository users;
    private PermissionCheckerImpl permission;
    private ApiRepository apis;
    private ApiResponseRepository responses;
    private ApiTagRepository apiTags;
    private GroupRepository groups;
    private TagRepository tags;
    private ApiServiceImpl service;
    private AiPendingActionRepository drafts;
    private FileRepository files;
    private CustomVariableRepository variables;
    private GlobalHeaderRepository headers;

    @BeforeEach void setUp() {
        users = mock(UserRepository.class);
        permission = new PermissionCheckerImpl();
        ReflectionTestUtils.setField(permission, "userRepository", users);
        com.mockhub.system.repository.TeamRepository teams = mock(com.mockhub.system.repository.TeamRepository.class);
        com.mockhub.system.model.entity.Team activeTeam = new com.mockhub.system.model.entity.Team();
        when(teams.findById(anyString())).thenReturn(activeTeam);
        ReflectionTestUtils.setField(permission, "teamRepository", teams);
        when(users.findUserTeamRole("u", "a")).thenReturn("VIEWER");
        Map<String,Object> details = new HashMap<String,Object>();
        details.put("userId", "u"); details.put("globalRole", "USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", null, Collections.emptyList());
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
        apis = mock(ApiRepository.class); responses = mock(ApiResponseRepository.class);
        when(apis.write(any())).thenAnswer(invocation -> ((java.util.function.Supplier<?>)invocation.getArgument(0)).get());
        apiTags = mock(ApiTagRepository.class); groups = mock(GroupRepository.class); tags = mock(TagRepository.class);
        service = new ApiServiceImpl(apis, responses, apiTags, groups, tags, mock(TeamService.class), permission,
                new ObjectMapper(), mock(LogService.class), users, mock(MockFileStorageService.class));
        ApiDefinition api = new ApiDefinition(); api.setId("api"); api.setTeamId("a"); api.setCreatedBy("someone-else");
        when(apis.findById("api")).thenReturn(api);
        when(apis.findByIds(Collections.singletonList("api"))).thenReturn(Collections.singletonList(api));
        drafts = mock(AiPendingActionRepository.class); files = mock(FileRepository.class);
        when(drafts.write(any())).thenAnswer(invocation -> ((java.util.function.Supplier<?>)invocation.getArgument(0)).get());
        variables = mock(CustomVariableRepository.class); headers = mock(GlobalHeaderRepository.class);
    }
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings={"create", "edit", "body", "copy", "toggle", "delete", "batch-enable", "batch-delete",
            "group-create", "tag-create", "variable-create", "headers", "upload", "merge", "override",
            "ai-create", "ai-update", "ai-full-update"})
    void viewerCannotWriteThroughAnyDomainEntrypoint(String action) {
        ApiDefinitionDTO dto = dto();
        assertThrows(BizException.class, () -> {
            switch (action) {
                case "create": service.create(dto); break;
                case "edit": service.update("api", dto); break;
                case "body": service.updateResponseBody("api", "response", "{}"); break;
                case "copy": service.copy("api"); break;
                case "toggle": service.toggle("api"); break;
                case "delete": service.delete("api"); break;
                case "batch-enable": service.batch("enable", Collections.singletonList("api"), null); break;
                case "batch-delete": service.batch("delete", Collections.singletonList("api"), null); break;
                case "group-create": ApiGroup g = new ApiGroup(); g.setTeamId("a"); new GroupService(groups, apis, permission).create(g); break;
                case "tag-create": Tag t = new Tag(); t.setTeamId("a"); new TagService(tags, apiTags, permission,apis).create(t); break;
                case "variable-create": new CustomVariableService(variables, permission).createVariable("a", "test", ""); break;
                case "headers": new GlobalHeaderService(headers, permission).replaceAll("a", Collections.emptyList()); break;
                case "upload": new FileServerService(files, mock(TeamRepository.class), permission, temp.toString(), 1024).uploadManaged("a", null); break;
                case "merge": importer().importApis("a", new ImportExportData(), "merge"); break;
                case "override": importer().importApis("a", new ImportExportData(), "override"); break;
                case "ai-create": pending().createApiDraft(dto, "test"); break;
                case "ai-update": AiApiUpdateDraft update = new AiApiUpdateDraft(); update.setTeamId("a"); pending().createApiUpdateDraft(update, "test"); break;
                case "ai-full-update": AiApiFullUpdateDraft full = new AiApiFullUpdateDraft(); full.setTeamId("a"); pending().createApiFullUpdateDraft(full, "test"); break;
                default: throw new AssertionError(action);
            }
        });
        verify(apis, never()).insert(any()); verify(apis, never()).update(any());
        verify(apis, never()).deleteById(anyString());
        verifyNoInteractions(responses, apiTags, files, variables, headers);
        if(action.startsWith("ai-")) verify(drafts).write(any());
        verifyNoMoreInteractions(drafts);
        verify(groups, never()).insert(any()); verify(tags, never()).insert(any());
    }

    @Test void editorCannotOverwriteImportOrBatchDelete() {
        when(users.findUserTeamRole("u", "a")).thenReturn("EDITOR");
        assertEquals(40101, assertThrows(BizException.class,
                () -> importer().importApis("a", new ImportExportData(), "override")).getCode());
        assertEquals(40101, assertThrows(BizException.class,
                () -> service.batch("delete", Collections.singletonList("api"), null)).getCode());
        verify(apis, never()).batchDeleteByIds(anyList());
    }

    @Test void editorCannotAttachOtherTeamsGroupOrTags() {
        when(users.findUserTeamRole("u", "a")).thenReturn("EDITOR");
        ApiGroup group = new ApiGroup(); group.setId("foreign"); group.setTeamId("b");
        when(groups.findById("foreign")).thenReturn(group);
        ApiDefinitionDTO dto = dto(); dto.setGroupId("foreign");
        assertEquals(40102, assertThrows(BizException.class, () -> service.create(dto)).getCode());
        dto.setGroupId(null); dto.setTagIds(Collections.singletonList("foreign-tag"));
        Tag tag = new Tag(); tag.setId("foreign-tag"); tag.setTeamId("b");
        when(tags.findByIds(Collections.singletonList("foreign-tag"))).thenReturn(Collections.singletonList(tag));
        assertEquals(40102, assertThrows(BizException.class, () -> service.create(dto)).getCode());
        verify(apis, never()).insert(any()); verifyNoInteractions(responses, apiTags);
    }

    @Test void editorCannotMoveApiAcrossTeamsByChangingDto() {
        when(users.findUserTeamRole("u", "a")).thenReturn("EDITOR");
        ApiDefinitionDTO dto = dto(); dto.setTeamId("b");
        assertEquals(40102, assertThrows(BizException.class, () -> service.update("api", dto)).getCode());
        verify(apis, never()).update(any());
    }

    private ApiDefinitionDTO dto() {
        ApiDefinitionDTO dto = new ApiDefinitionDTO(); dto.setTeamId("a"); dto.setName("Test");
        dto.setPath("/test"); dto.setMethod("GET"); return dto;
    }
    private ImportExportService importer() {
        return new ImportExportService(apis, responses, groups, tags, apiTags, headers, mock(TeamService.class), permission,
                mock(MockFileStorageService.class),service);
    }
    private PendingActionService pending() {
        return new PendingActionService(drafts, new ObjectMapper(), service, permission, mock(LogService.class));
    }
}
