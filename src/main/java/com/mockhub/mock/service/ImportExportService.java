package com.mockhub.mock.service;

import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mock.model.dto.ImportExportData;
import com.mockhub.mock.model.dto.ImportResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.model.entity.GlobalHeader;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.repository.ApiRepository;
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
    private final GroupRepository groupRepository;
    private final TagRepository tagRepository;
    private final ApiTagRepository apiTagRepository;
    private final GlobalHeaderRepository globalHeaderRepository;
    private final TeamService teamService;
    private final PermissionChecker permissionChecker;

    public ImportExportService(ApiRepository apiRepository,
                               GroupRepository groupRepository,
                               TagRepository tagRepository,
                               ApiTagRepository apiTagRepository,
                               GlobalHeaderRepository globalHeaderRepository,
                               TeamService teamService,
                               PermissionChecker permissionChecker) {
        this.apiRepository = apiRepository;
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
        data.setVersion("1.0");
        data.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        data.setTeamName(team != null ? team.getName() : "");
        data.setGroups(groupRepository.findByTeamId(teamId));
        data.setTags(tagRepository.findByTeamId(teamId));
        data.setApis(apiRepository.findByTeamId(teamId));
        data.setGlobalHeaders(globalHeaderRepository.findByTeamId(teamId));

        log.info("导出团队数据: teamId={}, apis={}, groups={}, tags={}, globalHeaders={}",
                teamId,
                data.getApis() != null ? data.getApis().size() : 0,
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

        // 导入接口
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
                    imported++;
                }
            }
        }

        log.info("导入完成: teamId={}, mode={}, imported={}, skipped={}, overridden={}",
                teamId, mode, imported, skipped, overridden);

        return new ImportResult(imported, skipped, overridden);
    }
}
