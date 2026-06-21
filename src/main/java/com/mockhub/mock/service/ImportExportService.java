package com.mockhub.mock.service;

import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mock.model.dto.ImportExportData;
import com.mockhub.mock.model.dto.ImportResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.model.entity.GlobalHeader;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.ApiTagRepository;
import com.mockhub.mock.repository.GlobalHeaderRepository;
import com.mockhub.mock.repository.GroupRepository;
import com.mockhub.mock.repository.TagRepository;
import com.mockhub.mock.service.match.ResponseValidator;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 接口导入导出 Service
 * <p>
 * 导出：将团队的全部分组、标签、接口、全局响应头组装为 ImportExportData JSON。
 * 导入：根据合并（merge）或覆盖（override）模式将数据导入到目标团队。
 * <ul>
 *   <li>合并模式：同团队内 path+method 已存在的跳过，新接口追加</li>
 *   <li>覆盖模式：同团队内 path+method 已存在的覆盖，新接口追加</li>
 * </ul>
 */
@Service
public class ImportExportService {

    private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);

    private final ApiRepository apiRepository;
    private final ApiResponseRepository apiResponseRepository;
    private final GroupRepository groupRepository;
    private final TagRepository tagRepository;
    private final ApiTagRepository apiTagRepository;
    private final GlobalHeaderRepository globalHeaderRepository;
    private final TeamService teamService;
    private final PermissionChecker permissionChecker;
    private final MockFileStorageService mockFileStorageService;

    public ImportExportService(ApiRepository apiRepository,
                               ApiResponseRepository apiResponseRepository,
                               GroupRepository groupRepository,
                               TagRepository tagRepository,
                               ApiTagRepository apiTagRepository,
                               GlobalHeaderRepository globalHeaderRepository,
                               TeamService teamService,
                               PermissionChecker permissionChecker,
                               MockFileStorageService mockFileStorageService) {
        this.apiRepository = apiRepository;
        this.apiResponseRepository = apiResponseRepository;
        this.groupRepository = groupRepository;
        this.tagRepository = tagRepository;
        this.apiTagRepository = apiTagRepository;
        this.globalHeaderRepository = globalHeaderRepository;
        this.teamService = teamService;
        this.permissionChecker = permissionChecker;
        this.mockFileStorageService = mockFileStorageService;
    }

    /**
     * 导出团队的全部数据
     *
     * @param teamId 团队 ID
     * @return 导出数据结构
     */
    public ImportExportData exportTeam(String teamId) {
        permissionChecker.checkTeamAccess(teamId);

        Team team = teamService.getById(teamId);

        ImportExportData data = new ImportExportData();
        data.setVersion("2.0");
        data.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        data.setTeamName(team != null ? team.getName() : "");
        data.setGroups(groupRepository.findByTeamId(teamId));
        data.setTags(tagRepository.findByTeamId(teamId));

        List<ApiDefinition> apis = apiRepository.findByTeamId(teamId);
        data.setApis(apis);

        // 导出所有接口的返回体
        List<ApiResponse> allResponses = new ArrayList<ApiResponse>();
        for (ApiDefinition api : apis) {
            allResponses.addAll(apiResponseRepository.findByApiId(api.getId()));
        }
        data.setApiResponses(allResponses);

        data.setGlobalHeaders(globalHeaderRepository.findByTeamId(teamId));

        log.info("导出团队数据: teamId={}, apis={}, responses={}, groups={}, tags={}, globalHeaders={}",
                teamId,
                data.getApis() != null ? data.getApis().size() : 0,
                allResponses.size(),
                data.getGroups() != null ? data.getGroups().size() : 0,
                data.getTags() != null ? data.getTags().size() : 0,
                data.getGlobalHeaders() != null ? data.getGlobalHeaders().size() : 0);

        return data;
    }

    public ExportPackage exportTeamPackage(String teamId) {
        return buildExportPackage(exportTeam(teamId));
    }

    public ExportPackage exportApisPackage(String teamId, List<String> apiIds) {
        permissionChecker.checkTeamAccess(teamId);
        if (apiIds == null || apiIds.isEmpty()) {
            return exportTeamPackage(teamId);
        }

        Team team = teamService.getById(teamId);
        List<ApiDefinition> apis = new ArrayList<ApiDefinition>();
        for (String apiId : apiIds) {
            ApiDefinition api = apiRepository.findById(apiId);
            if (api != null && teamId.equals(api.getTeamId())) {
                apis.add(api);
            }
        }

        ImportExportData data = new ImportExportData();
        data.setVersion("2.1");
        data.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        data.setTeamName(team != null ? team.getName() : "");
        data.setGroups(groupRepository.findByTeamId(teamId));
        data.setTags(tagRepository.findByTeamId(teamId));
        data.setApis(apis);

        List<ApiResponse> allResponses = new ArrayList<ApiResponse>();
        for (ApiDefinition api : apis) {
            allResponses.addAll(apiResponseRepository.findByApiId(api.getId()));
        }
        data.setApiResponses(allResponses);
        data.setGlobalHeaders(globalHeaderRepository.findByTeamId(teamId));
        return buildExportPackage(data);
    }

    private ExportPackage buildExportPackage(ImportExportData data) {
        Map<String, Resource> files = new HashMap<String, Resource>();
        if (data.getApiResponses() != null) {
            Set<String> exported = new HashSet<String>();
            for (ApiResponse response : data.getApiResponses()) {
                if (response == null
                        || !"FILE".equalsIgnoreCase(response.getBodyType())
                        || response.getFilePath() == null
                        || response.getFilePath().trim().isEmpty()
                        || exported.contains(response.getFilePath())) {
                    continue;
                }
                try {
                    files.put(response.getFilePath(), mockFileStorageService.loadAsResource(response.getFilePath()));
                    exported.add(response.getFilePath());
                } catch (Exception e) {
                    log.warn("导出文件响应实体失败，跳过文件实体: filePath={}, reason={}",
                            response.getFilePath(), e.getMessage());
                }
            }
        }
        return new ExportPackage(data, files);
    }

    /**
     * 导入接口数据到目标团队
     *
     * @param teamId 目标团队 ID
     * @param data   导入数据
     * @param mode   导入模式："merge"（合并）或 "override"（覆盖）
     * @return 导入结果统计
     */
    public ImportResult importApis(String teamId, ImportExportData data, String mode) {
        return importApis(teamId, data, mode, Collections.<String, FileBundleEntry>emptyMap());
    }

    public ImportResult importApis(String teamId, ImportExportData data, String mode,
                                   Map<String, FileBundleEntry> bundledFiles) {
        permissionChecker.checkTeamAccess(teamId);

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        String userId = SecurityContextUtil.getCurrentUserId();
        boolean isOverride = "override".equalsIgnoreCase(mode);

        int imported = 0;
        int skipped = 0;
        int overridden = 0;

        // 导入分组：建立旧 ID → 新 ID 映射
        Map<String, String> groupIdMap = new HashMap<String, String>();
        if (data.getGroups() != null) {
            for (ApiGroup group : data.getGroups()) {
                String oldId = group.getId();
                group.setId(UUID.randomUUID().toString());
                group.setTeamId(teamId);
                group.setCreatedAt(now);
                groupRepository.insert(group);
                groupIdMap.put(oldId, group.getId());
            }
            log.info("导入分组: 数量={}", data.getGroups().size());
        }

        // 导入标签：建立旧 ID → 新 ID 映射
        Map<String, String> tagIdMap = new HashMap<String, String>();
        if (data.getTags() != null) {
            for (Tag tag : data.getTags()) {
                String oldId = tag.getId();
                tag.setId(UUID.randomUUID().toString());
                tag.setTeamId(teamId);
                tagRepository.insert(tag);
                tagIdMap.put(oldId, tag.getId());
            }
            log.info("导入标签: 数量={}", data.getTags().size());
        }

        // 导入接口：建立旧 apiId → 新 apiId 映射
        Map<String, String> apiIdMap = new HashMap<String, String>();
        if (data.getApis() != null) {
            for (ApiDefinition api : data.getApis()) {
                // 检查同团队内 path+method 是否已存在
                List<ApiDefinition> existingList = apiRepository.findByTeamIdAndPathAndMethod(
                        teamId, api.getPath(), api.getMethod());

                if (!existingList.isEmpty()) {
                    if (isOverride) {
                        // 覆盖模式：更新已有接口
                        ApiDefinition existing = existingList.get(0);
                        existing.setName(api.getName());
                        existing.setDescription(api.getDescription());
                        existing.setType(api.getType());
                        existing.setResponseCode(api.getResponseCode());
                        existing.setContentType(api.getContentType());
                        existing.setResponseBody(api.getResponseBody());
                        existing.setDelayMs(api.getDelayMs());
                        existing.setGlobalHeaderOverrides(api.getGlobalHeaderOverrides());
                        existing.setSoapConfig(api.getSoapConfig());
                        existing.setUpdatedAt(now);
                        existing.setUpdatedBy(userId);

                        // 映射 groupId
                        if (api.getGroupId() != null && groupIdMap.containsKey(api.getGroupId())) {
                            existing.setGroupId(groupIdMap.get(api.getGroupId()));
                        }

                        apiRepository.update(existing);
                        // 记录 ID 映射，用于后续导入返回体
                        apiIdMap.put(api.getId(), existing.getId());
                        // 覆盖模式下清除旧返回体
                        apiResponseRepository.deleteByApiId(existing.getId());
                        overridden++;
                    } else {
                        // 合并模式：跳过已存在的
                        skipped++;
                    }
                } else {
                    // 新接口：插入
                    String oldId = api.getId();
                    api.setId(UUID.randomUUID().toString());
                    api.setTeamId(teamId);
                    api.setCreatedBy(userId);
                    api.setCreatedAt(now);
                    api.setUpdatedAt(now);
                    api.setUpdatedBy(userId);

                    // 映射 groupId
                    if (api.getGroupId() != null && groupIdMap.containsKey(api.getGroupId())) {
                        api.setGroupId(groupIdMap.get(api.getGroupId()));
                    } else {
                        api.setGroupId(null);
                    }

                    apiRepository.insert(api);
                    apiIdMap.put(oldId, api.getId());
                    imported++;
                }
            }
        }

        // 导入返回体
        if (data.getApiResponses() != null && !data.getApiResponses().isEmpty()) {
            // 先按新 apiId 分组 + 逐组校验（v1.4.3 新增条件响应相关约束，避免写入后数据异常）
            Map<String, List<ApiResponse>> grouped = new HashMap<String, List<ApiResponse>>();
            for (ApiResponse resp : data.getApiResponses()) {
                String newApiId = apiIdMap.get(resp.getApiId());
                if (newApiId == null) {
                    // 对应的接口未导入（合并模式下被跳过），跳过此返回体
                    continue;
                }
                resp.setId(UUID.randomUUID().toString());
                resp.setApiId(newApiId);
                if ("FILE".equalsIgnoreCase(resp.getBodyType())
                        && resp.getFilePath() != null
                        && bundledFiles != null
                        && bundledFiles.containsKey(resp.getFilePath())) {
                    FileBundleEntry entry = bundledFiles.get(resp.getFilePath());
                    MockFileStorageService.StoredMockFile stored = mockFileStorageService.store(
                            teamId,
                            resp.getFileName() != null ? resp.getFileName() : entry.getFileName(),
                            resp.getContentType(),
                            entry.getData().length,
                            new ByteArrayInputStream(entry.getData()));
                    resp.setFilePath(stored.getFilePath());
                    resp.setFileName(stored.getFileName());
                    resp.setFileSize(stored.getFileSize());
                }
                resp.setCreatedAt(now);
                resp.setUpdatedAt(now);
                List<ApiResponse> list = grouped.get(newApiId);
                if (list == null) {
                    list = new ArrayList<ApiResponse>();
                    grouped.put(newApiId, list);
                }
                list.add(resp);
            }
            for (List<ApiResponse> group : grouped.values()) {
                ResponseValidator.validateEntities(group);
            }
            int respImported = 0;
            for (List<ApiResponse> group : grouped.values()) {
                for (ApiResponse resp : group) {
                    apiResponseRepository.insert(resp);
                    respImported++;
                }
            }
            log.info("导入返回体: 数量={}", respImported);
        } else if (!apiIdMap.isEmpty()) {
            // 兼容旧版导出文件（无 apiResponses 字段）：为每个导入的 REST 接口创建默认返回体
            for (Map.Entry<String, String> entry : apiIdMap.entrySet()) {
                String newApiId = entry.getValue();
                // 检查是否已有返回体（覆盖模式下可能已被上面的逻辑清空）
                if (apiResponseRepository.countByApiId(newApiId) == 0) {
                    ApiDefinition importedApi = apiRepository.findById(newApiId);
                    if (importedApi != null && "REST".equals(importedApi.getType())) {
                        ApiResponse defaultResp = new ApiResponse();
                        defaultResp.setId(UUID.randomUUID().toString());
                        defaultResp.setApiId(newApiId);
                        defaultResp.setName("Default");
                        defaultResp.setResponseCode(importedApi.getResponseCode());
                        defaultResp.setContentType(importedApi.getContentType());
                        defaultResp.setResponseBody(importedApi.getResponseBody());
                        defaultResp.setDelayMs(importedApi.getDelayMs());
                        defaultResp.setActive(true);
                        defaultResp.setSortOrder(0);
                        defaultResp.setCreatedAt(now);
                        defaultResp.setUpdatedAt(now);
                        apiResponseRepository.insert(defaultResp);
                    }
                }
            }
        }

        log.info("导入完成: teamId={}, mode={}, imported={}, skipped={}, overridden={}",
                teamId, mode, imported, skipped, overridden);

        return new ImportResult(imported, skipped, overridden);
    }

    public static class ExportPackage {
        private final ImportExportData data;
        private final Map<String, Resource> files;

        public ExportPackage(ImportExportData data, Map<String, Resource> files) {
            this.data = data;
            this.files = files;
        }

        public ImportExportData getData() {
            return data;
        }

        public Map<String, Resource> getFiles() {
            return files;
        }

        public boolean hasFiles() {
            return files != null && !files.isEmpty();
        }
    }

    public static class FileBundleEntry {
        private final String fileName;
        private final byte[] data;

        public FileBundleEntry(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getData() {
            return data;
        }
    }
}
