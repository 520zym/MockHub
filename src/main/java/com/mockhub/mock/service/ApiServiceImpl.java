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
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.ApiTagRepository;
import com.mockhub.mock.repository.TagRepository;
import com.mockhub.log.LogService;
import com.mockhub.log.model.OperationLog;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final TagRepository tagRepository;
    private final TeamService teamService;
    private final PermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    private final LogService logService;

    public ApiServiceImpl(ApiRepository apiRepository,
                          ApiResponseRepository apiResponseRepository,
                          ApiTagRepository apiTagRepository,
                          TagRepository tagRepository,
                          TeamService teamService,
                          PermissionChecker permissionChecker,
                          ObjectMapper objectMapper,
                          LogService logService) {
        this.apiRepository = apiRepository;
        this.apiResponseRepository = apiResponseRepository;
        this.apiTagRepository = apiTagRepository;
        this.tagRepository = tagRepository;
        this.teamService = teamService;
        this.permissionChecker = permissionChecker;
        this.objectMapper = objectMapper;
        this.logService = logService;
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
                                            Boolean enabled, String keyword, String tagId,
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
        List<ApiDefinition> apis = apiRepository.findAll(teamIds, teamId, groupId, method, enabled, keyword, tagId, offset, size);
        long total = apiRepository.count(teamIds, teamId, groupId, method, enabled, keyword, tagId);

        // 转换为 VO，填充关联数据
        List<ApiDefinitionVO> voList = new ArrayList<ApiDefinitionVO>();
        for (ApiDefinition api : apis) {
            ApiDefinitionVO vo = convertToVO(api);
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

        // 校验同团队内 path+method 唯一性
        List<ApiDefinition> existing = apiRepository.findByTeamIdAndPathAndMethod(
                dto.getTeamId(), dto.getPath(), dto.getMethod());
        if (!existing.isEmpty()) {
            throw new BizException(40401, "同团队内路径+方法已存在");
        }

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
        api.setPath(dto.getPath());
        api.setResponseCode(dto.getResponseCode());
        api.setContentType(dto.getContentType() != null ? dto.getContentType() : "application/json");
        api.setResponseBody(dto.getResponseBody());
        api.setDelayMs(dto.getDelayMs());
        api.setEnabled(dto.isEnabled());
        api.setGlobalHeaderOverrides(serializeMap(dto.getGlobalHeaderOverrides()));
        api.setSoapConfig(serializeObject(dto.getSoapConfig()));
        api.setCreatedBy(userId);
        api.setCreatedAt(now);
        api.setUpdatedAt(now);
        api.setUpdatedBy(userId);

        apiRepository.insert(api);
        log.info("创建接口: id={}, name={}, method={}, path={}", api.getId(), api.getName(), api.getMethod(), api.getPath());

        // 保存返回体
        saveResponses(api.getId(), dto.getResponses(), now);

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
        String newPath = dto.getPath() != null ? dto.getPath() : existing.getPath();
        String newMethod = dto.getMethod() != null ? dto.getMethod() : existing.getMethod();
        if (!newPath.equals(existing.getPath()) || !newMethod.equals(existing.getMethod())) {
            List<ApiDefinition> conflict = apiRepository.findByTeamIdAndPathAndMethod(
                    existing.getTeamId(), newPath, newMethod);
            if (!conflict.isEmpty() && !conflict.get(0).getId().equals(id)) {
                throw new BizException(40401, "同团队内路径+方法已存在");
            }
        }

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
        existing.setUpdatedAt(now);
        existing.setUpdatedBy(userId);

        apiRepository.update(existing);
        log.info("更新接口: id={}, name={}", id, existing.getName());

        // 替换返回体
        saveResponses(id, dto.getResponses(), now);

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
        // 删除接口
        apiRepository.deleteById(id);
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
        vo.setCreatedBy(api.getCreatedBy());
        vo.setCreatedAt(api.getCreatedAt());
        vo.setUpdatedAt(api.getUpdatedAt());

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

        // 填充分组名称（需要通过 GroupService 获取，但为避免循环依赖直接查）
        // 这里在 Controller 层或通过额外参数处理，VO 中 groupName 由 Controller 层填充
        // 暂不在此处填充 groupName，由 Controller 调用时补充

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
     * 保存接口的返回体列表
     * <p>
     * 如果 DTO 中提供了 responses 列表，则替换所有返回体；
     * 如果未提供（兼容旧版客户端），不做处理，保留已有数据。
     *
     * @param apiId     接口 ID
     * @param responses 返回体 DTO 列表
     * @param now       当前时间
     */
    private void saveResponses(String apiId, List<ApiResponseDTO> responses, String now) {
        if (responses == null) {
            return;
        }

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
            entity.setDelayMs(dto.getDelayMs());
            entity.setActive(dto.isActive());
            entity.setSortOrder(dto.getSortOrder());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entities.add(entity);
        }

        apiResponseRepository.replaceAll(apiId, entities);
        log.debug("保存接口 {} 的返回体，共 {} 个", apiId, entities.size());
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
}
