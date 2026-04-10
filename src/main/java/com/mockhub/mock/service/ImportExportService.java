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
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public ImportExportService(ApiRepository apiRepository,
                               ApiResponseRepository apiResponseRepository,
                               GroupRepository groupRepository,
                               TagRepository tagRepository,
                               ApiTagRepository apiTagRepository,
                               GlobalHeaderRepository globalHeaderRepository,
                               TeamService teamService,
                               PermissionChecker permissionChecker) {
        this.apiRepository = apiRepository;
        this.apiResponseRepository = apiResponseRepository;
        this.groupRepository = groupRepository;
        this.tagRepository = tagRepository;
        this.apiTagRepository = apiTagRepository;
        this.globalHeaderRepository = globalHeaderRepository;
        this.teamService = teamService;
        this.permissionChecker = permissionChecker;
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

    /**
     * 导入接口数据到目标团队
     *
     * @param teamId 目标团队 ID
     * @param data   导入数据
     * @param mode   导入模式："merge"（合并）或 "override"（覆盖）
     * @return 导入结果统计
     */
    public ImportResult importApis(String teamId, ImportExportData data, String mode) {
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
            int respImported = 0;
            for (ApiResponse resp : data.getApiResponses()) {
                String newApiId = apiIdMap.get(resp.getApiId());
                if (newApiId == null) {
                    // 对应的接口未导入（合并模式下被跳过），跳过此返回体
                    continue;
                }
                resp.setId(UUID.randomUUID().toString());
                resp.setApiId(newApiId);
                resp.setCreatedAt(now);
                resp.setUpdatedAt(now);
                apiResponseRepository.insert(resp);
                respImported++;
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
}
