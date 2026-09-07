package com.mockhub.mock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiDefinitionDetailVO;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.model.dto.ApiMatchResult;
import com.mockhub.mock.model.dto.ApiResponseDTO;
import com.mockhub.mock.model.dto.BatchApiResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.ApiTagRepository;
import com.mockhub.mock.repository.GroupRepository;
import com.mockhub.mock.repository.TagRepository;
import com.mockhub.mock.service.match.ResponseValidator;
import com.mockhub.log.service.LogService;
import com.mockhub.log.model.OperationLog;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口定义 Service 实现
 * <p>
 * 包含路径匹配算法、CRUD 操作、权限校验等核心逻辑。
 */
@Service
public class ApiServiceImpl implements ApiService {

    private static final Logger log = LoggerFactory.getLogger(ApiServiceImpl.class);

    /**
     * 用于从配置路径中提取 {xxx} 占位符名称的正则
     * 例如 /api/user/{id} 中提取 "id"
     */
    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    private final ApiRepository apiRepository;
    private final ApiResponseRepository apiResponseRepository;
    private final ApiTagRepository apiTagRepository;
    private final GroupRepository groupRepository;
    private final TagRepository tagRepository;
    private final TeamService teamService;
    private final PermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    private final LogService logService;
    private final com.mockhub.system.repository.UserRepository userRepository;
    private final MockFileStorageService fileStorageService;

    public ApiServiceImpl(ApiRepository apiRepository,
                          ApiResponseRepository apiResponseRepository,
                          ApiTagRepository apiTagRepository,
                          GroupRepository groupRepository,
                          TagRepository tagRepository,
                          TeamService teamService,
                          PermissionChecker permissionChecker,
                          ObjectMapper objectMapper,
                          LogService logService,
                          com.mockhub.system.repository.UserRepository userRepository,
                          MockFileStorageService fileStorageService) {
        this.apiRepository = apiRepository;
        this.apiResponseRepository = apiResponseRepository;
        this.apiTagRepository = apiTagRepository;
        this.groupRepository = groupRepository;
        this.tagRepository = tagRepository;
        this.teamService = teamService;
        this.permissionChecker = permissionChecker;
        this.objectMapper = objectMapper;
        this.logService = logService;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 记录操作日志的工具方法
     */
    private void recordOperation(String action, String targetType, String targetId, String targetName, String detail, String teamId) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setId(UUID.randomUUID().toString());
            opLog.setTeamId(teamId);
            opLog.setUserId(SecurityContextUtil.getCurrentUserId());
            opLog.setUsername(SecurityContextUtil.getCurrentUsername());
            opLog.setAction(action);
            opLog.setTargetType(targetType);
            opLog.setTargetId(targetId);
            opLog.setTargetName(targetName);
            opLog.setDetail(detail);
            opLog.setCreatedAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            logService.logOperation(opLog);
        } catch (Exception e) {
            log.warn("记录操作日志失败: {}", e.getMessage());
        }
    }

    // ==================== 路径匹配算法 ====================

    /**
     * 查找匹配的接口定义
     * <p>
     * 算法分两阶段：
     * 1. 精确匹配：遍历所有不含 {xxx} 的路径，完全一致则命中
     * 2. 路径参数匹配：遍历所有含 {xxx} 的路径，编译为正则进行匹配
     *
     * @param teamId 团队 ID
     * @param method HTTP 方法
     * @param path   请求路径
     * @return 匹配结果，未匹配返回 null
     */
    @Override
    public ApiMatchResult findMatch(String teamId, String method, String path) {
        // 查出该团队所有已启用的、方法匹配的接口定义
        List<ApiDefinition> candidates = apiRepository.findByTeamIdAndMethodAndEnabled(teamId, method, true);

        if (candidates.isEmpty()) {
            log.debug("无候选接口: teamId={}, method={}, path={}", teamId, method, path);
            return null;
        }

        // 第一阶段：精确匹配（不含路径参数的配置路径）
        for (ApiDefinition api : candidates) {
            if (!containsPlaceholder(api.getPath())) {
                if (api.getPath().equals(path)) {
                    log.debug("精确匹配成功: apiId={}, path={}", api.getId(), path);
                    return new ApiMatchResult(api, Collections.<String, String>emptyMap());
                }
            }
        }

        // 第二阶段：路径参数匹配（含 {xxx} 的配置路径）
        for (ApiDefinition api : candidates) {
            if (containsPlaceholder(api.getPath())) {
                // 从配置路径中提取占位符名称列表
                List<String> paramNames = extractParamNames(api.getPath());

                // 将配置路径编译为正则：{xxx} → ([^/]+)，整体加首尾锚定
                String regex = "^" + api.getPath().replaceAll("\\{[^}]+\\}", "([^/]+)") + "$";
                Matcher matcher = Pattern.compile(regex).matcher(path);

                if (matcher.matches()) {
                    // 按顺序提取路径参数值
                    Map<String, String> pathVariables = new LinkedHashMap<String, String>();
                    for (int i = 0; i < paramNames.size(); i++) {
                        pathVariables.put(paramNames.get(i), matcher.group(i + 1));
                    }
                    log.debug("路径参数匹配成功: apiId={}, configPath={}, requestPath={}, params={}",
                            api.getId(), api.getPath(), path, pathVariables);
                    return new ApiMatchResult(api, pathVariables);
                }
            }
        }

        log.debug("未匹配到接口: teamId={}, method={}, path={}", teamId, method, path);
        return null;
    }

    /**
     * 判断路径是否包含 {xxx} 占位符
     */
    private boolean containsPlaceholder(String path) {
        return path != null && path.contains("{");
    }

    /**
     * 从配置路径中提取所有 {xxx} 占位符的名称，按出现顺序返回
     * 例如 /api/order/{orderId}/item/{itemId} → ["orderId", "itemId"]
     */
    private List<String> extractParamNames(String path) {
        List<String> names = new ArrayList<String>();
        Matcher matcher = PARAM_NAME_PATTERN.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    // ==================== CRUD 操作 ====================

    @Override
    public PageResult<ApiDefinitionVO> list(String teamId, String groupId, String method,
                                            Boolean enabled, String keyword, List<String> tagIds,
                                            String type, String sortBy, String sortDir,
                                            int page, int size) {
        // 确定当前用户可访问的团队范围
        List<String> teamIds = null;
        if (!SecurityContextUtil.isSuperAdmin()) {
            teamIds = SecurityContextUtil.getCurrentTeamIds();
            if (teamIds.isEmpty()) {
                // 用户没有任何团队，返回空列表
                return PageResult.of(Collections.<ApiDefinitionVO>emptyList(), 0L, page, size);
            }
        }

        // 如果指定了团队，校验访问权限
        if (teamId != null && !teamId.isEmpty()) {
            permissionChecker.checkTeamAccess(teamId);
        }

        int offset = (page - 1) * size;
        List<ApiDefinition> apis = apiRepository.findAll(teamIds, teamId, groupId, method, enabled, keyword, tagIds, type, sortBy, sortDir, offset, size);
        long total = apiRepository.count(teamIds, teamId, groupId, method, enabled, keyword, tagIds, type);

        // 一次性查全量用户，构 id → 显示名 映射，避免 N+1 查询。
        // 用户表通常很小（< 1000 条），全表扫的成本远低于 N 次单查。
        java.util.Map<String, String> userIdToName = new java.util.HashMap<String, String>();
        try {
            for (com.mockhub.system.model.entity.User u : userRepository.findAll()) {
                String displayName = u.getDisplayName() != null && !u.getDisplayName().isEmpty()
                        ? u.getDisplayName() : u.getUsername();
                userIdToName.put(u.getId(), displayName);
            }
        } catch (Exception e) {
            log.warn("加载用户字典失败，列表的 createdByName 将为空", e);
        }

        // 转换为 VO，填充关联数据
        List<ApiDefinitionVO> voList = new ArrayList<ApiDefinitionVO>();
        for (ApiDefinition api : apis) {
            ApiDefinitionVO vo = convertToVO(api);
            // 回填创建人显示名
            if (api.getCreatedBy() != null) {
                vo.setCreatedByName(userIdToName.get(api.getCreatedBy()));
            }
            voList.add(vo);
        }

        return PageResult.of(voList, total, page, size);
    }

    @Override
    public ApiDefinitionDetailVO getById(String id) {
        ApiDefinition api = apiRepository.findById(id);
        if (api == null) {
            throw new BizException(40402, "接口不存在");
        }
        // 校验团队访问权限
        permissionChecker.checkTeamAccess(api.getTeamId());

        // 构建详情 VO
        ApiDefinitionDetailVO detail = new ApiDefinitionDetailVO();
        detail.setId(api.getId());
        detail.setTeamId(api.getTeamId());
        detail.setGroupId(api.getGroupId());
        detail.setType(api.getType());
        detail.setName(api.getName());
        detail.setDescription(api.getDescription());
        detail.setMethod(api.getMethod());
        detail.setPath(api.getPath());
        detail.setResponseCode(api.getResponseCode());
        detail.setContentType(api.getContentType());
        detail.setResponseBody(api.getResponseBody());
        detail.setDelayMs(api.getDelayMs());
        detail.setEnabled(api.isEnabled());
        detail.setGlobalHeaderOverrides(api.getGlobalHeaderOverrides());
        detail.setSoapConfig(api.getSoapConfig());
        detail.setResponseMode(normalizeResponseMode(api.getResponseMode()));
        detail.setCreatedBy(api.getCreatedBy());
        detail.setCreatedAt(api.getCreatedAt());
        detail.setUpdatedAt(api.getUpdatedAt());
        detail.setUpdatedBy(api.getUpdatedBy());

        // 填充返回体列表
        List<ApiResponse> responses = apiResponseRepository.findByApiId(id);
        detail.setResponses(responses);

        // 填充标签列表
        List<String> tagIds = apiTagRepository.findTagIdsByApiId(id);
        if (!tagIds.isEmpty()) {
            detail.setTags(tagRepository.findByIds(tagIds));
        } else {
            detail.setTags(Collections.<Tag>emptyList());
        }

        return detail;
    }

    @Override
    public ApiDefinition create(ApiDefinitionDTO dto) {
        // 校验团队访问权限
        permissionChecker.checkTeamAccess(dto.getTeamId());

        String normalizedPath = normalizeApiPath(dto.getPath());

        // 校验同团队内 path+method 唯一性
        List<ApiDefinition> existing = apiRepository.findByTeamIdAndPathAndMethod(
                dto.getTeamId(), normalizedPath, dto.getMethod());
        if (!existing.isEmpty()) {
            throw new BizException(40401, "同团队内路径+方法已存在");
        }

        // 所有模式与返回体约束必须在首次写库前完成，避免非事务路径留下半成品接口。
        Map<String, String> responseModes = responseModes(dto);
        validateResponseConfiguration(dto.getResponses(), null, responseModes);

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        String userId = SecurityContextUtil.getCurrentUserId();

        ApiDefinition api = new ApiDefinition();
        api.setId(UUID.randomUUID().toString());
        api.setTeamId(dto.getTeamId());
        api.setGroupId(dto.getGroupId());
        api.setType(dto.getType() != null ? dto.getType() : "REST");
        api.setName(dto.getName());
        api.setDescription(dto.getDescription());
        api.setMethod(dto.getMethod());
        api.setPath(normalizedPath);
        api.setResponseCode(dto.getResponseCode());
        api.setContentType(dto.getContentType() != null ? dto.getContentType() : "application/json");
        api.setResponseBody(dto.getResponseBody());
        api.setDelayMs(dto.getDelayMs());
        api.setEnabled(dto.isEnabled());
        api.setGlobalHeaderOverrides(serializeMap(dto.getGlobalHeaderOverrides()));
        api.setSoapConfig(serializeObject(dto.getSoapConfig()));
        api.setResponseMode(normalizeResponseMode(dto.getResponseMode()));
        api.setCreatedBy(userId);
        api.setCreatedAt(now);
        api.setUpdatedAt(now);
        api.setUpdatedBy(userId);

        apiRepository.insert(api);
        log.info("创建接口: id={}, name={}, method={}, path={}", api.getId(), api.getName(), api.getMethod(), api.getPath());

        // 保存返回体
        saveResponses(api.getId(), dto.getResponses(), now, responseModes);

        // 保存标签关联
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            apiTagRepository.replaceTagsForApi(api.getId(), dto.getTagIds());
        }

        recordOperation("CREATE", "API", api.getId(), api.getName(),
                "创建接口 " + api.getMethod() + " " + api.getPath(), api.getTeamId());

        return api;
    }

    @Override
    public ApiDefinition update(String id, ApiDefinitionDTO dto) {
        ApiDefinition existing = apiRepository.findById(id);
        if (existing == null) {
            throw new BizException(40402, "接口不存在");
        }

        // 校验团队访问权限
        permissionChecker.checkTeamAccess(existing.getTeamId());

        // 如果修改了 path 或 method，校验唯一性
        String newPath = dto.getPath() != null ? normalizeApiPath(dto.getPath()) : existing.getPath();
        String newMethod = dto.getMethod() != null ? dto.getMethod() : existing.getMethod();
        if (!newPath.equals(existing.getPath()) || !newMethod.equals(existing.getMethod())) {
            List<ApiDefinition> conflict = apiRepository.findByTeamIdAndPathAndMethod(
                    existing.getTeamId(), newPath, newMethod);
            if (!conflict.isEmpty() && !conflict.get(0).getId().equals(id)) {
                throw new BizException(40401, "同团队内路径+方法已存在");
            }
        }

        String targetRestMode = dto.getResponseMode() == null
                ? normalizeResponseMode(existing.getResponseMode())
                : normalizeResponseMode(dto.getResponseMode());
        // 更新时 responses 未传是旧客户端兼容路径；仍必须以现有返回体验证切换后的模式。
        Map<String, String> responseModes = responseModes(dto, targetRestMode, existing.getSoapConfig());
        List<ApiResponse> existingResponses = dto.getResponses() == null
                ? apiResponseRepository.findByApiId(id) : null;
        validateResponseConfiguration(dto.getResponses(), existingResponses, responseModes);

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        String userId = SecurityContextUtil.getCurrentUserId();

        existing.setGroupId(dto.getGroupId());
        existing.setType(dto.getType() != null ? dto.getType() : existing.getType());
        existing.setName(dto.getName() != null ? dto.getName() : existing.getName());
        existing.setDescription(dto.getDescription());
        existing.setMethod(newMethod);
        existing.setPath(newPath);
        existing.setResponseCode(dto.getResponseCode());
        existing.setContentType(dto.getContentType() != null ? dto.getContentType() : existing.getContentType());
        existing.setResponseBody(dto.getResponseBody());
        existing.setDelayMs(dto.getDelayMs());
        existing.setEnabled(dto.isEnabled());
        existing.setGlobalHeaderOverrides(serializeMap(dto.getGlobalHeaderOverrides()));
        existing.setSoapConfig(serializeObject(dto.getSoapConfig()));
        existing.setResponseMode(targetRestMode);
        existing.setUpdatedAt(now);
        existing.setUpdatedBy(userId);

        apiRepository.update(existing);
        log.info("更新接口: id={}, name={}", id, existing.getName());

        // 替换返回体
        saveResponses(id, dto.getResponses(), now, responseModes);

        recordOperation("UPDATE", "API", id, existing.getName(),
                "修改接口 " + existing.getMethod() + " " + existing.getPath(), existing.getTeamId());

        // 更新标签关联
        apiTagRepository.replaceTagsForApi(id, dto.getTagIds());

        return existing;
    }

    @Override
    public void delete(String id) {
        ApiDefinition api = apiRepository.findById(id);
        if (api == null) {
            throw new BizException(40402, "接口不存在");
        }

        // 校验团队访问权限
        permissionChecker.checkTeamAccess(api.getTeamId());

        // 删除标签关联
        apiTagRepository.deleteByApiId(id);
        Set<String> oldFilePaths = collectFilePaths(apiResponseRepository.findByApiId(id));
        apiResponseRepository.deleteByApiId(id);
        // 删除接口
        apiRepository.deleteById(id);
        cleanupUnreferencedFiles(oldFilePaths);
        log.info("删除接口: id={}, name={}, path={}", id, api.getName(), api.getPath());
        recordOperation("DELETE", "API", id, api.getName(),
                "删除接口 " + api.getMethod() + " " + api.getPath(), api.getTeamId());
    }

    @Override
    public ApiDefinition copy(String id) {
        ApiDefinition source = apiRepository.findById(id);
        if (source == null) {
            throw new BizException(40402, "接口不存在");
        }

        // 校验团队访问权限
        permissionChecker.checkTeamAccess(source.getTeamId());

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        String userId = SecurityContextUtil.getCurrentUserId();

        ApiDefinition copy = new ApiDefinition();
        copy.setId(UUID.randomUUID().toString());
        copy.setTeamId(source.getTeamId());
        copy.setGroupId(source.getGroupId());
        copy.setType(source.getType());
        copy.setName(source.getName() + " (副本)");
        copy.setDescription(source.getDescription());
        copy.setMethod(source.getMethod());
        copy.setPath(source.getPath() + "-copy");
        copy.setResponseCode(source.getResponseCode());
        copy.setContentType(source.getContentType());
        copy.setResponseBody(source.getResponseBody());
        copy.setDelayMs(source.getDelayMs());
        copy.setEnabled(false); // 副本默认禁用，避免路径冲突
        copy.setGlobalHeaderOverrides(source.getGlobalHeaderOverrides());
        copy.setSoapConfig(source.getSoapConfig());
        copy.setResponseMode(source.getResponseMode());
        copy.setCreatedBy(userId);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        copy.setUpdatedBy(userId);

        apiRepository.insert(copy);
        log.info("复制接口: sourceId={}, newId={}, newPath={}", id, copy.getId(), copy.getPath());

        // 复制返回体
        List<ApiResponse> sourceResponses = apiResponseRepository.findByApiId(id);
        for (ApiResponse srcResp : sourceResponses) {
            ApiResponse copyResp = new ApiResponse();
            copyResp.setId(UUID.randomUUID().toString());
            copyResp.setApiId(copy.getId());
            copyResp.setSoapOperationName(srcResp.getSoapOperationName());
            copyResp.setName(srcResp.getName());
            copyResp.setResponseCode(srcResp.getResponseCode());
            copyResp.setContentType(srcResp.getContentType());
            copyResp.setResponseBody(srcResp.getResponseBody());
            copyResp.setBodyType(srcResp.getBodyType());
            copyResp.setFileName(srcResp.getFileName());
            copyResp.setFilePath(srcResp.getFilePath());
            copyResp.setDownloadName(srcResp.getDownloadName());
            copyResp.setFileSize(srcResp.getFileSize());
            copyResp.setDelayMs(srcResp.getDelayMs());
            copyResp.setActive(srcResp.isActive());
            copyResp.setSortOrder(srcResp.getSortOrder());
            copyResp.setConditions(srcResp.getConditions());
            copyResp.setCreatedAt(now);
            copyResp.setUpdatedAt(now);
            apiResponseRepository.insert(copyResp);
        }

        // 复制标签关联
        List<String> tagIds = apiTagRepository.findTagIdsByApiId(id);
        if (!tagIds.isEmpty()) {
            apiTagRepository.replaceTagsForApi(copy.getId(), tagIds);
        }

        return copy;
    }

    @Override
    public boolean toggle(String id) {
        ApiDefinition api = apiRepository.findById(id);
        if (api == null) {
            throw new BizException(40402, "接口不存在");
        }

        // 校验团队访问权限
        permissionChecker.checkTeamAccess(api.getTeamId());

        boolean newEnabled = !api.isEnabled();
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        apiRepository.updateEnabled(id, newEnabled, now);
        log.info("切换接口状态: id={}, enabled={}", id, newEnabled);

        return newEnabled;
    }

    @Override
    public BatchApiResult batch(String action, List<String> ids, String targetGroupId) {
        if (ids == null || ids.isEmpty()) {
            return new BatchApiResult(0);
        }
        if (action == null || action.isEmpty()) {
            throw new BizException(40400, "缺少 action 参数");
        }

        // 1. 一次性把目标接口加载出来（用于权限校验和审计日志）
        List<ApiDefinition> targets = apiRepository.findByIds(ids);
        if (targets.isEmpty()) {
            log.warn("批量操作未匹配到任何接口: action={}, ids={}", action, ids);
            return new BatchApiResult(0);
        }

        // 2. 按团队聚合一次去重，避免同一团队多次重复校验
        java.util.Set<String> teamIds = new java.util.HashSet<String>();
        for (ApiDefinition api : targets) {
            teamIds.add(api.getTeamId());
        }
        for (String tid : teamIds) {
            permissionChecker.checkTeamAccess(tid);
        }

        // 3. 收集 targets 实际命中的 id 列表（已存在的，用于 SQL 批处理）
        List<String> validIds = new ArrayList<String>(targets.size());
        for (ApiDefinition api : targets) {
            validIds.add(api.getId());
        }

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        int affected;

        if ("enable".equals(action)) {
            affected = apiRepository.batchUpdateEnabled(validIds, true, now);
            log.info("批量启用接口: count={}", affected);
        } else if ("disable".equals(action)) {
            affected = apiRepository.batchUpdateEnabled(validIds, false, now);
            log.info("批量禁用接口: count={}", affected);
        } else if ("delete".equals(action)) {
            // 先清理标签关联和返回体，再删主表
            Set<String> oldFilePaths = new HashSet<String>();
            for (String apiId : validIds) {
                oldFilePaths.addAll(collectFilePaths(apiResponseRepository.findByApiId(apiId)));
            }
            apiTagRepository.batchDeleteByApiIds(validIds);
            apiResponseRepository.batchDeleteByApiIds(validIds);
            affected = apiRepository.batchDeleteByIds(validIds);
            cleanupUnreferencedFiles(oldFilePaths);
            log.info("批量删除接口: count={}", affected);
        } else if ("move-group".equals(action)) {
            affected = apiRepository.batchUpdateGroup(validIds, targetGroupId, now);
            log.info("批量移动分组: count={}, targetGroupId={}", affected, targetGroupId);
        } else {
            throw new BizException(40400, "不支持的批量操作: " + action);
        }

        // 4. 一条汇总操作日志（按团队分别记录，方便按团队查日志）
        for (String tid : teamIds) {
            int teamCount = 0;
            for (ApiDefinition api : targets) {
                if (tid.equals(api.getTeamId())) {
                    teamCount++;
                }
            }
            recordOperation("BATCH_" + action.toUpperCase().replace('-', '_'),
                    "API", null, null,
                    "批量" + actionDisplayName(action) + " " + teamCount + " 个接口", tid);
        }

        return new BatchApiResult(affected);
    }

    @Override
    public String findConflictingApiName(String teamId, String method, String path, String excludeId) {
        if (teamId == null || teamId.isEmpty() || method == null || path == null || path.isEmpty()) {
            return null;
        }
        String normalizedPath = normalizeApiPathForCheck(path);
        if (normalizedPath == null) {
            return null;
        }
        // 校验团队访问权限：避免被人当成枚举接口
        permissionChecker.checkTeamAccess(teamId);

        List<ApiDefinition> matches = apiRepository.findByTeamIdAndPathAndMethod(teamId, normalizedPath, method);
        for (ApiDefinition api : matches) {
            if (excludeId != null && excludeId.equals(api.getId())) {
                continue;
            }
            return api.getName();
        }
        return null;
    }

    /**
     * 规范化接口维护路径：接口定义只保存 URL path，不保存 query/fragment。
     * Mock 分发入口收到请求时也是按 path 匹配，query 交给条件返回规则处理。
     */
    private String normalizeApiPath(String path) {
        String normalized = normalizeApiPathForCheck(path);
        if (normalized == null) {
            throw new BizException(40400, "接口路径不能为空");
        }
        return normalized;
    }

    private String normalizeApiPathForCheck(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    /**
     * 把 action 翻成展示名（只用于操作日志的 detail 字段）
     */
    private String actionDisplayName(String action) {
        if ("enable".equals(action)) return "启用";
        if ("disable".equals(action)) return "禁用";
        if ("delete".equals(action)) return "删除";
        if ("move-group".equals(action)) return "移动分组";
        return action;
    }

    // ==================== 内部方法 ====================

    /**
     * 将 ApiDefinition 转换为列表展示用的 VO
     * 填充团队信息、分组名称、标签列表、创建人名称
     */
    private ApiDefinitionVO convertToVO(ApiDefinition api) {
        ApiDefinitionVO vo = new ApiDefinitionVO();
        vo.setId(api.getId());
        vo.setTeamId(api.getTeamId());
        vo.setGroupId(api.getGroupId());
        vo.setType(api.getType());
        vo.setName(api.getName());
        vo.setDescription(api.getDescription());
        vo.setMethod(api.getMethod());
        vo.setPath(api.getPath());
        vo.setResponseCode(api.getResponseCode());
        vo.setContentType(api.getContentType());
        vo.setDelayMs(api.getDelayMs());
        vo.setEnabled(api.isEnabled());
        vo.setResponseMode(normalizeResponseMode(api.getResponseMode()));
        vo.setCreatedBy(api.getCreatedBy());
        vo.setCreatedAt(api.getCreatedAt());
        vo.setUpdatedAt(api.getUpdatedAt());
        vo.setHitCount(api.getHitCount());
        vo.setLastCalledAt(api.getLastCalledAt());

        // 填充团队信息
        try {
            Team team = teamService.getById(api.getTeamId());
            if (team != null) {
                vo.setTeamName(team.getName());
                vo.setTeamIdentifier(team.getIdentifier());
                vo.setTeamColor(team.getColor());
            }
        } catch (Exception e) {
            log.warn("查询团队信息失败: teamId={}", api.getTeamId(), e);
        }

        // 填充分组名称：列表页可能处于“所有接口”视图，前端无法只靠当前团队分组列表映射。
        if (api.getGroupId() != null && !api.getGroupId().isEmpty()) {
            try {
                com.mockhub.mock.model.entity.ApiGroup group = groupRepository.findById(api.getGroupId());
                if (group != null) {
                    vo.setGroupName(group.getName());
                }
            } catch (Exception e) {
                log.warn("查询分组信息失败: groupId={}", api.getGroupId(), e);
            }
        }

        // 填充标签列表
        List<String> tagIds = apiTagRepository.findTagIdsByApiId(api.getId());
        if (!tagIds.isEmpty()) {
            List<Tag> tags = tagRepository.findByIds(tagIds);
            vo.setTags(tags);
        } else {
            vo.setTags(Collections.<Tag>emptyList());
        }

        // createdByName 需要 UserService 查询，由 Controller 层或通过 SQL JOIN 补充
        // 此处暂不填充

        // 填充返回体摘要信息
        List<ApiResponse> respSummary = apiResponseRepository.findSummaryByApiId(api.getId());
        vo.setResponseCount(respSummary.size());
        for (ApiResponse resp : respSummary) {
            if (resp.isActive()) {
                vo.setActiveResponseName(resp.getName());
                break;
            }
        }

        return vo;
    }

    /**
     * 规范化 REST 选择模式。空值代表旧客户端/旧数据，保持原有条件匹配行为。
     */
    private String normalizeResponseMode(String responseMode) {
        if (responseMode == null || responseMode.trim().isEmpty()) {
            return "CONDITION";
        }
        if ("CONDITION".equalsIgnoreCase(responseMode) || "RANDOM".equalsIgnoreCase(responseMode)) {
            return responseMode.toUpperCase();
        }
        throw new BizException(40418, "返回体选择模式仅支持 CONDITION 或 RANDOM：" + responseMode);
    }

    /**
     * 为 REST 和 SOAP operation 建立校验用模式表；SOAP 模式存放在 soapConfig JSON 中。
     */
    private Map<String, String> responseModes(ApiDefinitionDTO dto) {
        return responseModes(dto, "CONDITION");
    }

    private Map<String, String> responseModes(ApiDefinitionDTO dto, String defaultRestMode) {
        return responseModes(dto, defaultRestMode, null);
    }

    private Map<String, String> responseModes(ApiDefinitionDTO dto, String defaultRestMode,
                                              String existingSoapConfig) {
        Map<String, String> modes = new java.util.HashMap<String, String>();
        modes.put("__REST__", dto.getResponseMode() == null
                ? normalizeResponseMode(defaultRestMode)
                : normalizeResponseMode(dto.getResponseMode()));
        String soapConfigJson = dto.getSoapConfig() == null
                ? existingSoapConfig : serializeObject(dto.getSoapConfig());
        if (soapConfigJson == null || soapConfigJson.trim().isEmpty()) {
            return modes;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode operations = objectMapper.readTree(soapConfigJson).path("operations");
            if (operations.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode operation : operations) {
                    String name = operation.path("operationName").asText(null);
                    if (name != null && !name.isEmpty()) {
                        modes.put(name, normalizeResponseMode(operation.path("responseMode").asText(null)));
                    }
                }
            }
        } catch (JsonProcessingException e) {
            throw new BizException(40418, "SOAP 配置无法解析，无法校验返回体选择模式");
        }
        return modes;
    }

    private void validateResponseConfiguration(List<ApiResponseDTO> incoming,
                                               List<ApiResponse> existing,
                                               Map<String, String> responseModes) {
        if (incoming != null) {
            ResponseValidator.validateDtos(incoming, responseModes);
        } else if (existing != null) {
            ResponseValidator.validateEntities(existing, responseModes);
        } else {
            // 创建接口未传 responses 时仍需拒绝 RANDOM 空池；CONDITION 保持旧客户端兼容。
            ResponseValidator.validateDtos(null, responseModes);
        }
    }

    /**
     * 保存接口的返回体列表
     * <p>
     * 如果 DTO 中提供了 responses 列表，则替换所有返回体；
     * 如果未提供（兼容旧版客户端），不做处理，保留已有数据。
     *
     * @param apiId     接口 ID
     * @param responses 返回体 DTO 列表
     * @param now       当前时间
     */
    private void saveResponses(String apiId, List<ApiResponseDTO> responses, String now,
                               Map<String, String> responseModes) {
        if (responses == null) {
            return;
        }

        // v1.4.3 新增：多启用 + 条件匹配前置校验，失败抛 BizException 由全局处理器转为 40410~40416 错误码
        ResponseValidator.validateDtos(responses, responseModes);

        List<ApiResponse> entities = new ArrayList<ApiResponse>();
        for (int i = 0; i < responses.size(); i++) {
            ApiResponseDTO dto = responses.get(i);
            ApiResponse entity = new ApiResponse();
            entity.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString());
            entity.setApiId(apiId);
            entity.setSoapOperationName(dto.getSoapOperationName());
            entity.setName(dto.getName() != null ? dto.getName() : "Response " + (i + 1));
            entity.setResponseCode(dto.getResponseCode());
            entity.setContentType(dto.getContentType() != null ? dto.getContentType() : "application/json");
            entity.setResponseBody(dto.getResponseBody());
            entity.setBodyType(dto.getBodyType() != null ? dto.getBodyType() : "TEXT");
            entity.setFileName(dto.getFileName());
            entity.setFilePath(dto.getFilePath());
            entity.setDownloadName(dto.getDownloadName());
            entity.setFileSize(dto.getFileSize());
            entity.setDelayMs(dto.getDelayMs());
            entity.setActive(dto.isActive());
            entity.setSortOrder(dto.getSortOrder());
            entity.setConditions(dto.getConditions());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entities.add(entity);
        }

        Set<String> oldFilePaths = collectFilePaths(apiResponseRepository.findByApiId(apiId));
        apiResponseRepository.replaceAll(apiId, entities);
        cleanupUnreferencedFiles(oldFilePaths);
        log.debug("保存接口 {} 的返回体，共 {} 个", apiId, entities.size());
    }

    private Set<String> collectFilePaths(List<ApiResponse> responses) {
        Set<String> paths = new HashSet<String>();
        if (responses == null) {
            return paths;
        }
        for (ApiResponse response : responses) {
            if (response != null
                    && "FILE".equalsIgnoreCase(response.getBodyType())
                    && response.getFilePath() != null
                    && !response.getFilePath().trim().isEmpty()) {
                paths.add(response.getFilePath());
            }
        }
        return paths;
    }

    private void cleanupUnreferencedFiles(Set<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }
        for (String filePath : filePaths) {
            if (apiResponseRepository.countByFilePath(filePath) == 0) {
                fileStorageService.deleteQuietly(filePath);
            }
        }
    }

    /**
     * 将 Map 序列化为 JSON 字符串
     */
    private String serializeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("序列化 globalHeaderOverrides 失败", e);
            return null;
        }
    }

    /**
     * 将对象序列化为 JSON 字符串（用于 soapConfig）
     */
    private String serializeObject(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("序列化 soapConfig 失败", e);
            return null;
        }
    }

    /**
     * 异步累加接口命中次数 + 更新 last_called_at。
     * <p>
     * 由 Spring 通过 AOP 代理切到独立线程池执行，不阻塞 Mock 响应主流程。
     * 任何异常仅打 warn 日志，吞掉不向上抛——统计写入失败不能影响 Mock 响应。
     * <p>
     * 接口在写入瞬间被并发删除时 update 影响 0 行属正常情况，不视为错误。
     *
     * @param apiId 命中的接口 ID
     */
    @Async
    @Override
    public void asyncIncrementHitCount(String apiId) {
        if (apiId == null || apiId.isEmpty()) {
            return;
        }
        try {
            String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
            apiRepository.incrementHitCount(apiId, now);
        } catch (Exception e) {
            log.warn("命中累加失败 apiId={}: {}", apiId, e.getMessage());
        }
    }
}
