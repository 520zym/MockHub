package com.mockhub.mock.service;

import com.mockhub.common.exception.UnresolvedPlaceholderException;
import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.dto.variable.CustomVariableDTO;
import com.mockhub.mock.model.entity.CustomVariable;
import com.mockhub.mock.model.entity.CustomVariableGroup;
import com.mockhub.mock.model.entity.CustomVariableValue;
import com.mockhub.mock.repository.CustomVariableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 自定义动态变量服务
 * <p>
 * 提供团队级的变量/候选值/分组的 CRUD 以及供 {@link DynamicVariableResolver}
 * 调用的快速随机挑选接口 {@link #randomPickValue(String, String, String, String)}。
 * <p>
 * 维护进程内缓存（{@link ConcurrentHashMap}），按团队 ID 作为 key，
 * 懒加载构建。任何写操作完成后按团队粒度整体失效（{@link #invalidateCache(String)}），
 * 下一次读取时重新从数据库加载。这种策略在团队数量有限（内部部署通常 &lt; 50）
 * 且写频率较低的场景下内存占用可控（见 spec 设计稿"内存占用评估"）。
 */
@Service
public class CustomVariableService {

    private static final Logger log = LoggerFactory.getLogger(CustomVariableService.class);

    /** 变量/分组名允许的正则：1~32 个字母/数字/下划线 */
    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{1,32}$");

    /** 禁用的变量名（保留给内置变量，大小写不敏感） */
    private static final Set<String> RESERVED_NAMES = new HashSet<String>(
            Arrays.asList("timestamp", "uuid", "date", "datetime", "random_int", "path"));

    /** 单团队最多变量数（硬性护栏） */
    public static final int MAX_VARIABLES_PER_TEAM = 100;

    /** 单变量最多候选值数（硬性护栏，对应 spec 设计） */
    public static final int MAX_VALUES_PER_VARIABLE = 10000;

    /** 单变量最多分组数 */
    public static final int MAX_GROUPS_PER_VARIABLE = 100;

    /** 单个值字符串最大长度 */
    public static final int MAX_VALUE_LENGTH = 256;

    /** 单个描述字符串最大长度 */
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    private final CustomVariableRepository repository;

    private final PermissionChecker permissionChecker;

    /** 团队级缓存：teamId → 该团队的所有变量解析视图 */
    private final ConcurrentHashMap<String, TeamVariableCache> cacheByTeam = new ConcurrentHashMap<String, TeamVariableCache>();

    private final Random random = new Random();

    @Autowired
    public CustomVariableService(CustomVariableRepository repository, PermissionChecker permissionChecker) {
        this.repository = repository;
        this.permissionChecker = permissionChecker;
    }

    // ==================== 查询接口（聚合视图） ====================

    /**
     * 列出团队的全部变量（聚合视图，含值与分组）
     * <p>
     * 需要团队访问权限（超管或该团队成员）。
     */
    public List<CustomVariableDTO> listByTeam(String teamId) {
        permissionChecker.checkTeamAccess(teamId);
        List<CustomVariable> variables = repository.findByTeamId(teamId);
        List<CustomVariableDTO> result = new ArrayList<CustomVariableDTO>();
        for (CustomVariable v : variables) {
            result.add(buildDTO(v));
        }
        return result;
    }

    /** 聚合一个变量的视图（值 + 分组 + 分组成员） */
    private CustomVariableDTO buildDTO(CustomVariable v) {
        CustomVariableDTO dto = new CustomVariableDTO();
        dto.setId(v.getId());
        dto.setTeamId(v.getTeamId());
        dto.setName(v.getName());
        dto.setDescription(v.getDescription());
        dto.setCreatedAt(v.getCreatedAt());
        dto.setUpdatedAt(v.getUpdatedAt());

        List<CustomVariableValue> values = repository.findValuesByVariableId(v.getId());
        List<CustomVariableDTO.ValueView> valueViews = new ArrayList<CustomVariableDTO.ValueView>();
        for (CustomVariableValue val : values) {
            CustomVariableDTO.ValueView vv = new CustomVariableDTO.ValueView();
            vv.setId(val.getId());
            vv.setValue(val.getValue());
            vv.setDescription(val.getDescription());
            vv.setSortOrder(val.getSortOrder());
            valueViews.add(vv);
        }
        dto.setValues(valueViews);

        List<CustomVariableGroup> groups = repository.findGroupsByVariableId(v.getId());
        Map<String, List<String>> groupValueIds = repository.findGroupValueIdsByVariableId(v.getId());
        List<CustomVariableDTO.GroupView> groupViews = new ArrayList<CustomVariableDTO.GroupView>();
        for (CustomVariableGroup g : groups) {
            CustomVariableDTO.GroupView gv = new CustomVariableDTO.GroupView();
            gv.setId(g.getId());
            gv.setName(g.getName());
            gv.setDescription(g.getDescription());
            List<String> ids = groupValueIds.get(g.getId());
            gv.setValueIds(ids != null ? ids : new ArrayList<String>());
            groupViews.add(gv);
        }
        dto.setGroups(groupViews);
        return dto;
    }

    // ==================== 变量 CRUD ====================

    /**
     * 创建变量（团队管理员或超管）
     */
    public CustomVariableDTO createVariable(String teamId, String name, String description) {
        permissionChecker.checkTeamAdmin(teamId);
        validateVariableName(name);
        if (repository.findByTeamIdAndName(teamId, name) != null) {
            throw new BizException(40201, "变量名已存在：" + name);
        }
        if (repository.countByTeamId(teamId) >= MAX_VARIABLES_PER_TEAM) {
            throw new BizException(40201, "单团队变量数量已达上限（" + MAX_VARIABLES_PER_TEAM + "）");
        }

        CustomVariable v = new CustomVariable();
        v.setId(UUID.randomUUID().toString());
        v.setTeamId(teamId);
        v.setName(name);
        v.setDescription(description);
        String now = LocalDateTime.now().toString();
        v.setCreatedAt(now);
        v.setUpdatedAt(now);
        repository.insertVariable(v);

        invalidateCache(teamId);
        log.info("创建自定义变量: teamId={}, name={}", teamId, name);
        return buildDTO(v);
    }

    /**
     * 更新变量（改名、改描述）
     */
    public void updateVariable(String teamId, String id, String name, String description) {
        permissionChecker.checkTeamAdmin(teamId);
        CustomVariable v = loadAndCheck(teamId, id);
        validateVariableName(name);
        // 改名时需再次唯一性校验
        if (!v.getName().equals(name)) {
            if (repository.findByTeamIdAndName(teamId, name) != null) {
                throw new BizException(40201, "变量名已存在：" + name);
            }
        }
        v.setName(name);
        v.setDescription(description);
        v.setUpdatedAt(LocalDateTime.now().toString());
        repository.updateVariable(v);
        invalidateCache(teamId);
    }

    /**
     * 删除变量（级联清理值、分组、关联）
     */
    public void deleteVariable(String teamId, String id) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, id);
        repository.deleteVariableCascade(id);
        invalidateCache(teamId);
        log.info("删除自定义变量: teamId={}, variableId={}", teamId, id);
    }

    // ==================== 候选值 CRUD ====================

    /**
     * 批量新增候选值，返回成功插入和跳过（已存在）的条数
     *
     * @param teamId      团队 ID
     * @param variableId  变量 ID
     * @param pairs       结构化的值列表（value + description）
     * @return int[2]：[0]=插入数，[1]=跳过数
     */
    public int[] batchInsertValues(String teamId, String variableId, List<ValuePair> pairs) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, variableId);
        if (pairs == null || pairs.isEmpty()) {
            return new int[]{0, 0};
        }

        // 先在入参内去重
        Map<String, ValuePair> deduped = new HashMap<String, ValuePair>();
        for (ValuePair p : pairs) {
            if (p == null || p.value == null || p.value.isEmpty()) continue;
            validateValueLength(p.value);
            if (p.description != null) {
                validateDescriptionLength(p.description);
            }
            if (!deduped.containsKey(p.value)) {
                deduped.put(p.value, p);
            }
        }

        // 与 DB 已存在的值比对
        long existingCount = repository.countValuesByVariableId(variableId);
        List<CustomVariableValue> toInsert = new ArrayList<CustomVariableValue>();
        int skipped = 0;
        int order = (int) existingCount;
        for (Map.Entry<String, ValuePair> e : deduped.entrySet()) {
            if (repository.findValueByVariableIdAndValue(variableId, e.getKey()) != null) {
                skipped++;
                continue;
            }
            CustomVariableValue v = new CustomVariableValue();
            v.setId(UUID.randomUUID().toString());
            v.setVariableId(variableId);
            v.setValue(e.getKey());
            v.setDescription(e.getValue().description);
            v.setSortOrder(order++);
            toInsert.add(v);
        }

        if (existingCount + toInsert.size() > MAX_VALUES_PER_VARIABLE) {
            throw new BizException(40201, "候选值数量超过上限（" + MAX_VALUES_PER_VARIABLE + "）");
        }

        repository.batchInsertValues(toInsert);
        invalidateCache(teamId);
        log.info("批量新增候选值: variableId={}, inserted={}, skipped={}",
                variableId, toInsert.size(), skipped);
        return new int[]{toInsert.size(), skipped};
    }

    /**
     * 单条新增候选值
     */
    public void addValue(String teamId, String variableId, String value, String description) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, variableId);
        validateValueLength(value);
        if (description != null) {
            validateDescriptionLength(description);
        }
        if (repository.findValueByVariableIdAndValue(variableId, value) != null) {
            throw new BizException(40201, "候选值已存在：" + value);
        }
        if (repository.countValuesByVariableId(variableId) >= MAX_VALUES_PER_VARIABLE) {
            throw new BizException(40201, "候选值数量超过上限（" + MAX_VALUES_PER_VARIABLE + "）");
        }
        CustomVariableValue v = new CustomVariableValue();
        v.setId(UUID.randomUUID().toString());
        v.setVariableId(variableId);
        v.setValue(value);
        v.setDescription(description);
        v.setSortOrder((int) repository.countValuesByVariableId(variableId));
        repository.insertValue(v);
        invalidateCache(teamId);
    }

    /**
     * 编辑候选值
     */
    public void updateValue(String teamId, String variableId, String valueId,
                            String value, String description, Integer sortOrder) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, variableId);
        CustomVariableValue existing = repository.findValueById(valueId);
        if (existing == null || !variableId.equals(existing.getVariableId())) {
            throw new BizException(40404, "候选值不存在");
        }
        validateValueLength(value);
        if (description != null) {
            validateDescriptionLength(description);
        }
        // 改值时需要检查是否会与其他行冲突
        if (!existing.getValue().equals(value)) {
            CustomVariableValue conflict = repository.findValueByVariableIdAndValue(variableId, value);
            if (conflict != null && !conflict.getId().equals(valueId)) {
                throw new BizException(40201, "候选值已存在：" + value);
            }
        }
        existing.setValue(value);
        existing.setDescription(description);
        if (sortOrder != null) {
            existing.setSortOrder(sortOrder);
        }
        repository.updateValue(existing);
        invalidateCache(teamId);
    }

    /**
     * 删除候选值
     */
    public void deleteValue(String teamId, String variableId, String valueId) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, variableId);
        CustomVariableValue existing = repository.findValueById(valueId);
        if (existing == null || !variableId.equals(existing.getVariableId())) {
            return;
        }
        repository.deleteValueCascade(valueId);
        invalidateCache(teamId);
    }

    // ==================== 分组 CRUD ====================

    /**
     * 创建分组（可选带初始成员 valueIds）
     */
    public CustomVariableDTO.GroupView createGroup(String teamId, String variableId,
                                                    String name, String description,
                                                    List<String> valueIds) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, variableId);
        validateGroupName(name);
        if (repository.findGroupByVariableIdAndName(variableId, name) != null) {
            throw new BizException(40201, "分组名已存在：" + name);
        }
        if (repository.countGroupsByVariableId(variableId) >= MAX_GROUPS_PER_VARIABLE) {
            throw new BizException(40201, "分组数量超过上限（" + MAX_GROUPS_PER_VARIABLE + "）");
        }

        CustomVariableGroup g = new CustomVariableGroup();
        g.setId(UUID.randomUUID().toString());
        g.setVariableId(variableId);
        g.setName(name);
        g.setDescription(description);
        repository.insertGroup(g);
        if (valueIds != null && !valueIds.isEmpty()) {
            validateValueIdsBelongToVariable(variableId, valueIds);
            repository.replaceGroupValues(g.getId(), valueIds);
        }
        invalidateCache(teamId);

        CustomVariableDTO.GroupView view = new CustomVariableDTO.GroupView();
        view.setId(g.getId());
        view.setName(g.getName());
        view.setDescription(g.getDescription());
        view.setValueIds(valueIds != null ? valueIds : new ArrayList<String>());
        return view;
    }

    /**
     * 更新分组（改名/描述/成员）
     */
    public void updateGroup(String teamId, String variableId, String groupId,
                            String name, String description, List<String> valueIds) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, variableId);
        CustomVariableGroup g = repository.findGroupById(groupId);
        if (g == null || !variableId.equals(g.getVariableId())) {
            throw new BizException(40404, "分组不存在");
        }
        validateGroupName(name);
        if (!g.getName().equals(name)) {
            if (repository.findGroupByVariableIdAndName(variableId, name) != null) {
                throw new BizException(40201, "分组名已存在：" + name);
            }
        }
        g.setName(name);
        g.setDescription(description);
        repository.updateGroup(g);
        if (valueIds != null) {
            validateValueIdsBelongToVariable(variableId, valueIds);
            repository.replaceGroupValues(groupId, valueIds);
        }
        invalidateCache(teamId);
    }

    /**
     * 删除分组
     */
    public void deleteGroup(String teamId, String variableId, String groupId) {
        permissionChecker.checkTeamAdmin(teamId);
        loadAndCheck(teamId, variableId);
        CustomVariableGroup g = repository.findGroupById(groupId);
        if (g == null || !variableId.equals(g.getVariableId())) {
            return;
        }
        repository.deleteGroupCascade(groupId);
        invalidateCache(teamId);
    }

    // ==================== 给 Resolver 用的快速接口 ====================

    /**
     * 检查指定团队是否存在名为 varName 的变量（不校验权限，供 resolver 快速判断）
     */
    public boolean hasVariable(String teamId, String varName) {
        TeamVariableCache cache = getOrLoadCache(teamId);
        return cache.byName.containsKey(varName);
    }

    /**
     * 从变量（或其分组）中随机挑选一个值
     * <p>
     * - groupName == null：从全部值随机挑选
     * - groupName != null：从指定分组随机挑选；若分组不存在或为空则抛
     *   {@link UnresolvedPlaceholderException} 由分发层转成 500 响应。
     * <p>
     * 不做权限校验：resolver 调用时已完成了 Mock 路由的团队解析，请求发起者
     * 未必是登录用户（Mock 请求通常无 JWT）。
     *
     * @param teamId         团队 ID
     * @param teamIdentifier 团队短标识（仅用于错误消息）
     * @param varName        变量名
     * @param groupName      分组名，可为 null
     * @return 随机选中的值字符串；若变量不存在返回 null（由 resolver 决定是否保留占位符）
     */
    public String randomPickValue(String teamId, String teamIdentifier,
                                  String varName, String groupName) {
        TeamVariableCache cache = getOrLoadCache(teamId);
        ResolvedVariable rv = cache.byName.get(varName);
        if (rv == null) {
            return null;
        }
        if (groupName == null) {
            if (rv.allValues.isEmpty()) {
                // 变量存在但一个值都没有：视为未定义，保留原样（不是 fail-fast 场景）
                return null;
            }
            return rv.allValues.get(random.nextInt(rv.allValues.size()));
        }
        // 带分组
        List<String> groupValues = rv.groupValues.get(groupName);
        if (groupValues == null || groupValues.isEmpty()) {
            throw new UnresolvedPlaceholderException(
                    teamIdentifier,
                    "{{" + varName + "." + groupName + "}}",
                    varName, groupName);
        }
        return groupValues.get(random.nextInt(groupValues.size()));
    }

    // ==================== 缓存管理 ====================

    /**
     * 获取团队缓存，未加载则懒加载
     */
    private TeamVariableCache getOrLoadCache(String teamId) {
        TeamVariableCache cache = cacheByTeam.get(teamId);
        if (cache != null) {
            return cache;
        }
        synchronized (cacheByTeam) {
            cache = cacheByTeam.get(teamId);
            if (cache != null) {
                return cache;
            }
            cache = loadCache(teamId);
            cacheByTeam.put(teamId, cache);
            return cache;
        }
    }

    /**
     * 从 DB 构建团队缓存
     */
    private TeamVariableCache loadCache(String teamId) {
        TeamVariableCache cache = new TeamVariableCache();
        List<CustomVariable> variables = repository.findByTeamId(teamId);
        for (CustomVariable v : variables) {
            List<CustomVariableValue> valueEntities = repository.findValuesByVariableId(v.getId());
            // valueId → value 映射（分组回填值用）
            Map<String, String> valueIdToValue = new HashMap<String, String>();
            List<String> allValues = new ArrayList<String>();
            for (CustomVariableValue ve : valueEntities) {
                valueIdToValue.put(ve.getId(), ve.getValue());
                allValues.add(ve.getValue());
            }
            List<CustomVariableGroup> groups = repository.findGroupsByVariableId(v.getId());
            Map<String, List<String>> groupValueIds = repository.findGroupValueIdsByVariableId(v.getId());
            Map<String, List<String>> groupValues = new HashMap<String, List<String>>();
            for (CustomVariableGroup g : groups) {
                List<String> ids = groupValueIds.get(g.getId());
                List<String> vals = new ArrayList<String>();
                if (ids != null) {
                    for (String vid : ids) {
                        String val = valueIdToValue.get(vid);
                        if (val != null) {
                            vals.add(val);
                        }
                    }
                }
                groupValues.put(g.getName(), vals);
            }
            ResolvedVariable rv = new ResolvedVariable();
            rv.name = v.getName();
            rv.allValues = Collections.unmodifiableList(allValues);
            rv.groupValues = groupValues;
            cache.byName.put(v.getName(), rv);
        }
        log.debug("加载团队变量缓存: teamId={}, variables={}", teamId, cache.byName.size());
        return cache;
    }

    /**
     * 失效指定团队的缓存（写操作后调用）
     */
    private void invalidateCache(String teamId) {
        cacheByTeam.remove(teamId);
    }

    // ==================== 工具与校验 ====================

    /** 加载变量并校验归属团队正确（防跨团队越权） */
    private CustomVariable loadAndCheck(String teamId, String variableId) {
        CustomVariable v = repository.findById(variableId);
        if (v == null || !teamId.equals(v.getTeamId())) {
            throw new BizException(40404, "变量不存在");
        }
        return v;
    }

    private void validateVariableName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BizException(40201, "变量名只允许 1~32 个字母/数字/下划线");
        }
        if (RESERVED_NAMES.contains(name.toLowerCase())) {
            throw new BizException(40201, "变量名与内置变量冲突：" + name);
        }
    }

    private void validateGroupName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BizException(40201, "分组名只允许 1~32 个字母/数字/下划线");
        }
    }

    private void validateValueLength(String value) {
        if (value == null || value.isEmpty()) {
            throw new BizException(40201, "候选值不能为空");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new BizException(40201, "候选值长度超过 " + MAX_VALUE_LENGTH);
        }
    }

    private void validateDescriptionLength(String desc) {
        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BizException(40201, "描述长度超过 " + MAX_DESCRIPTION_LENGTH);
        }
    }

    /** 校验 valueIds 全部属于指定变量 */
    private void validateValueIdsBelongToVariable(String variableId, List<String> valueIds) {
        if (valueIds == null || valueIds.isEmpty()) {
            return;
        }
        List<CustomVariableValue> allValues = repository.findValuesByVariableId(variableId);
        Set<String> validIds = new HashSet<String>();
        for (CustomVariableValue v : allValues) {
            validIds.add(v.getId());
        }
        for (String id : valueIds) {
            if (!validIds.contains(id)) {
                throw new BizException(40201, "分组包含非本变量的候选值 ID：" + id);
            }
        }
    }

    // ==================== 内部数据结构 ====================

    /**
     * 团队级缓存条目
     */
    private static class TeamVariableCache {
        final Map<String, ResolvedVariable> byName = new HashMap<String, ResolvedVariable>();
    }

    /**
     * 单个变量在缓存中的解析视图
     */
    private static class ResolvedVariable {
        String name;
        /** 变量下全部候选值（不可变），用于 {{xxx}} 全量随机挑选 */
        List<String> allValues;
        /** 分组名 → 该分组候选值列表，用于 {{xxx.yyy}} 分组随机挑选 */
        Map<String, List<String>> groupValues;
    }

    /**
     * 批量插入候选值的入参结构（由 Controller 层构造）
     */
    public static class ValuePair {
        public final String value;
        public final String description;

        public ValuePair(String value, String description) {
            this.value = value;
            this.description = description;
        }
    }
}
