package com.mockhub.mock.service;

import com.mockhub.common.util.DynamicVariableUtil;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.entity.GlobalHeader;
import com.mockhub.mock.repository.GlobalHeaderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全局响应头 Service
 * <p>
 * 管理团队级别的全局响应头配置，并提供 Mock 分发时的响应头构建逻辑。
 * <p>
 * 叠加规则（见 architecture.md）：
 * <ol>
 *   <li>加载团队全局响应头中 enabled=true 的记录，按 sortOrder 排序</li>
 *   <li>组装为 headerName → headerValue 的有序 Map</li>
 *   <li>应用接口级别覆盖：已有 key 替换，新 key 新增，空值删除</li>
 *   <li>对最终所有 value 执行动态变量替换</li>
 * </ol>
 */
@Service
public class GlobalHeaderService {

    private static final Logger log = LoggerFactory.getLogger(GlobalHeaderService.class);

    private final GlobalHeaderRepository globalHeaderRepository;
    private final PermissionChecker permissionChecker;

    public GlobalHeaderService(GlobalHeaderRepository globalHeaderRepository,
                               PermissionChecker permissionChecker) {
        this.globalHeaderRepository = globalHeaderRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 查询团队的所有全局响应头（含启用和禁用）
     *
     * @param teamId 团队 ID
     * @return 全局响应头列表，按 sortOrder 升序
     */
    public List<GlobalHeader> findByTeamId(String teamId) {
        permissionChecker.checkTeamAccess(teamId);
        return globalHeaderRepository.findByTeamId(teamId);
    }

    /**
     * 整体替换团队的全局响应头
     * <p>
     * 前端传入完整的响应头列表，后端先删后插。
     * 每条记录自动生成新的 UUID。
     *
     * @param teamId  团队 ID
     * @param headers 新的全局响应头列表
     */
    public void replaceAll(String teamId, List<GlobalHeader> headers) {
        permissionChecker.checkTeamAccess(teamId);

        // 为每条记录生成 ID
        if (headers != null) {
            for (GlobalHeader header : headers) {
                if (header.getId() == null || header.getId().isEmpty()) {
                    header.setId(UUID.randomUUID().toString());
                }
            }
        }

        globalHeaderRepository.replaceAll(teamId, headers);
        log.info("整体替换全局响应头: teamId={}, 数量={}", teamId, headers != null ? headers.size() : 0);
    }

    /**
     * 构建 Mock 响应的最终响应头 Map
     * <p>
     * 按全局响应头叠加规则处理：
     * <ol>
     *   <li>加载团队 enabled=true 的全局响应头，构建基础 Map</li>
     *   <li>应用接口级别覆盖（替换/新增/删除）</li>
     *   <li>对所有 value 执行动态变量替换</li>
     * </ol>
     *
     * @param teamId    团队 ID
     * @param overrides 接口级别覆盖配置，key 为 headerName，value 为空字符串表示删除；可为 null
     * @param pathVariables 路径参数，用于动态变量替换；可为 null
     * @return 最终的响应头 Map，key 为 headerName，value 为替换后的 headerValue
     */
    public Map<String, String> buildHeaders(String teamId, Map<String, String> overrides,
                                            Map<String, String> pathVariables) {
        // 1. 加载团队已启用的全局响应头
        List<GlobalHeader> enabledHeaders = globalHeaderRepository.findEnabledByTeamId(teamId);

        // 2. 构建基础 Map（LinkedHashMap 保持插入顺序）
        Map<String, String> headerMap = new LinkedHashMap<String, String>();
        for (GlobalHeader header : enabledHeaders) {
            headerMap.put(header.getHeaderName(), header.getHeaderValue());
        }

        log.debug("全局响应头基础 Map: teamId={}, headers={}", teamId, headerMap.keySet());

        // 3. 应用接口级别覆盖
        if (overrides != null && !overrides.isEmpty()) {
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value == null || value.isEmpty()) {
                    // 空值表示删除该 header
                    headerMap.remove(key);
                    log.debug("接口级覆盖 - 删除: {}", key);
                } else {
                    // 替换或新增
                    headerMap.put(key, value);
                    log.debug("接口级覆盖 - 设置: {}={}", key, value);
                }
            }
        }

        // 4. 对所有 value 执行动态变量替换
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            String replacedValue = DynamicVariableUtil.replace(entry.getValue(), pathVariables);
            result.put(entry.getKey(), replacedValue);
        }

        return result;
    }
}
