package com.mockhub.mock.service;

import com.mockhub.common.model.PageResult;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiDefinitionDetailVO;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.model.dto.ApiMatchResult;
import com.mockhub.mock.model.entity.ApiDefinition;

/**
 * 接口定义 Service 接口
 * <p>
 * 提供接口 CRUD、路径匹配、启用/禁用、复制等功能。
 */
public interface ApiService {

    /**
     * 查找匹配的接口定义（Mock 分发时调用）
     * <p>
     * 匹配规则：teamId + method + enabled=true + 路径匹配
     * 优先级：精确匹配 > 路径参数匹配
     *
     * @param teamId 团队 ID
     * @param method HTTP 方法
     * @param path   请求路径（不含 /mock/{identifier} 前缀）
     * @return 匹配结果，含匹配到的 ApiDefinition 和提取的路径参数；未匹配返回 null
     */
    ApiMatchResult findMatch(String teamId, String method, String path);

    /**
     * 分页查询接口列表
     *
     * @param teamId  按团队筛选（可为 null）
     * @param groupId 按分组筛选（可为 null）
     * @param method  按方法筛选（可为 null）
     * @param enabled 按启用状态筛选（可为 null）
     * @param keyword 按名称或路径模糊搜索（可为 null）
     * @param tagId   按标签筛选（可为 null）
     * @param page    页码
     * @param size    每页条数
     * @return 分页结果
     */
    PageResult<ApiDefinitionVO> list(String teamId, String groupId, String method,
                                     Boolean enabled, String keyword, String tagId,
                                     int page, int size);

    /**
     * 获取接口详情（含 responseBody、返回体列表、标签列表）
     *
     * @param id 接口 ID
     * @return 接口定义详情（含所有字段、返回体列表、标签列表）
     */
    ApiDefinitionDetailVO getById(String id);

    /**
     * 创建接口
     *
     * @param dto 创建请求体
     * @return 创建后的接口定义
     */
    ApiDefinition create(ApiDefinitionDTO dto);

    /**
     * 更新接口
     *
     * @param id  接口 ID
     * @param dto 更新请求体
     * @return 更新后的接口定义
     */
    ApiDefinition update(String id, ApiDefinitionDTO dto);

    /**
     * 删除接口
     *
     * @param id 接口 ID
     */
    void delete(String id);

    /**
     * 复制接口
     * <p>
     * 名称追加" (副本)"，路径追加"-copy"。
     *
     * @param id 源接口 ID
     * @return 新创建的接口定义
     */
    ApiDefinition copy(String id);

    /**
     * 切换启用/禁用状态
     *
     * @param id 接口 ID
     * @return 新的启用状态
     */
    boolean toggle(String id);
}
