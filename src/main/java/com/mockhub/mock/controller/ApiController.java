package com.mockhub.mock.controller;

import com.mockhub.common.model.PageResult;
import com.mockhub.common.model.Result;
import com.mockhub.mock.model.dto.ApiDefinitionDTO;
import com.mockhub.mock.model.dto.ApiDefinitionDetailVO;
import com.mockhub.mock.model.dto.ApiDefinitionVO;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 接口管理 Controller
 * <p>
 * 提供接口定义的 CRUD、复制、启用/禁用等操作端点。
 * 所有端点返回 {@link Result} 统一包装格式。
 */
@RestController
@RequestMapping("/api/apis")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ApiService apiService;

    public ApiController(ApiService apiService) {
        this.apiService = apiService;
    }

    /**
     * 接口列表（分页）
     * <p>
     * 自动按当前用户所属团队过滤，超级管理员看到全部。
     *
     * @param teamId  按团队筛选（可选）
     * @param groupId 按分组筛选（可选）
     * @param method  按 HTTP 方法筛选（可选）
     * @param enabled 按启用状态筛选（可选）
     * @param keyword 按名称或路径模糊搜索（可选）
     * @param tagId   按标签筛选（可选）
     * @param page    页码，默认 1
     * @param size    每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<ApiDefinitionVO>> list(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tagId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<ApiDefinitionVO> result = apiService.list(teamId, groupId, method, enabled, keyword, tagId, page, size);
        return Result.ok(result);
    }

    /**
     * 获取接口详情（含返回体列表、标签列表）
     *
     * @param id 接口 ID
     * @return 完整的接口定义详情
     */
    @GetMapping("/{id}")
    public Result<ApiDefinitionDetailVO> getById(@PathVariable String id) {
        ApiDefinitionDetailVO detail = apiService.getById(id);
        return Result.ok(detail);
    }

    /**
     * 创建接口
     *
     * @param dto 创建请求体
     * @return 创建后的接口定义
     */
    @PostMapping
    public Result<ApiDefinition> create(@RequestBody ApiDefinitionDTO dto) {
        ApiDefinition api = apiService.create(dto);
        return Result.ok(api);
    }

    /**
     * 更新接口
     *
     * @param id  接口 ID
     * @param dto 更新请求体
     * @return 更新后的接口定义
     */
    @PutMapping("/{id}")
    public Result<ApiDefinition> update(@PathVariable String id, @RequestBody ApiDefinitionDTO dto) {
        ApiDefinition api = apiService.update(id, dto);
        return Result.ok(api);
    }

    /**
     * 删除接口
     *
     * @param id 接口 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        apiService.delete(id);
        return Result.ok();
    }

    /**
     * 复制接口
     * <p>
     * 自动在名称后追加" (副本)"，路径后追加 "-copy"。
     *
     * @param id 源接口 ID
     * @return 新创建的接口定义
     */
    @PostMapping("/{id}/copy")
    public Result<ApiDefinition> copy(@PathVariable String id) {
        ApiDefinition api = apiService.copy(id);
        return Result.ok(api);
    }

    /**
     * 切换启用/禁用状态
     * <p>
     * 无请求体，服务端取反当前状态。
     *
     * @param id 接口 ID
     * @return 新的启用状态 {"enabled": true/false}
     */
    @PutMapping("/{id}/toggle")
    public Result<Map<String, Boolean>> toggle(@PathVariable String id) {
        boolean newEnabled = apiService.toggle(id);
        return Result.ok(Collections.singletonMap("enabled", newEnabled));
    }
}
