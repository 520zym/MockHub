package com.mockhub.mock.web.api;

import com.mockhub.common.model.Result;
import com.mockhub.mock.model.dto.variable.BatchValuesRequest;
import com.mockhub.mock.model.dto.variable.BatchValuesResult;
import com.mockhub.mock.model.dto.variable.CustomVariableDTO;
import com.mockhub.mock.model.dto.variable.GroupRequest;
import com.mockhub.mock.model.dto.variable.ValueRequest;
import com.mockhub.mock.model.dto.variable.VariableRequest;
import com.mockhub.mock.service.CustomVariableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义动态变量管理控制器
 * <p>
 * 提供团队级的变量、候选值、分组的 CRUD。所有端点以团队为路径前缀，
 * 权限校验在 Service 层统一通过 PermissionChecker 完成：
 * - 读：checkTeamAccess（团队成员或超管）
 * - 写：checkTeamAdmin（团队管理员或超管）
 * <p>
 * 统一返回 Result 包装格式。
 */
@RestController
@RequestMapping("/api/teams/{teamId}/variables")
public class CustomVariableController {

    @Autowired
    private CustomVariableService service;

    // ==================== 变量 ====================

    /**
     * GET /api/teams/{teamId}/variables — 列出团队的全部变量（聚合视图）
     */
    @GetMapping
    public Result<List<CustomVariableDTO>> list(@PathVariable String teamId) {
        return Result.ok(service.listByTeam(teamId));
    }

    /**
     * POST /api/teams/{teamId}/variables — 创建变量
     */
    @PostMapping
    public Result<CustomVariableDTO> create(@PathVariable String teamId,
                                            @RequestBody VariableRequest req) {
        return Result.ok(service.createVariable(teamId, req.getName(), req.getDescription()));
    }

    /**
     * PUT /api/teams/{teamId}/variables/{id} — 改变量（改名/描述）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String teamId,
                               @PathVariable String id,
                               @RequestBody VariableRequest req) {
        service.updateVariable(teamId, id, req.getName(), req.getDescription());
        return Result.ok();
    }

    /**
     * DELETE /api/teams/{teamId}/variables/{id} — 删变量（级联清理值、分组、关联）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String teamId, @PathVariable String id) {
        service.deleteVariable(teamId, id);
        return Result.ok();
    }

    // ==================== 候选值 ====================

    /**
     * POST /api/teams/{teamId}/variables/{id}/values/batch — 批量新增候选值
     */
    @PostMapping("/{id}/values/batch")
    public Result<BatchValuesResult> batchInsertValues(@PathVariable String teamId,
                                                       @PathVariable("id") String variableId,
                                                       @RequestBody BatchValuesRequest req) {
        List<CustomVariableService.ValuePair> pairs = new ArrayList<CustomVariableService.ValuePair>();
        if (req.getValues() != null) {
            for (ValueRequest vr : req.getValues()) {
                pairs.add(new CustomVariableService.ValuePair(vr.getValue(), vr.getDescription()));
            }
        }
        int[] counts = service.batchInsertValues(teamId, variableId, pairs);
        return Result.ok(new BatchValuesResult(counts[0], counts[1]));
    }

    /**
     * POST /api/teams/{teamId}/variables/{id}/values — 单条新增候选值
     */
    @PostMapping("/{id}/values")
    public Result<Void> addValue(@PathVariable String teamId,
                                 @PathVariable("id") String variableId,
                                 @RequestBody ValueRequest req) {
        service.addValue(teamId, variableId, req.getValue(), req.getDescription());
        return Result.ok();
    }

    /**
     * PUT /api/teams/{teamId}/variables/{id}/values/{vid} — 编辑候选值
     */
    @PutMapping("/{id}/values/{vid}")
    public Result<Void> updateValue(@PathVariable String teamId,
                                    @PathVariable("id") String variableId,
                                    @PathVariable("vid") String valueId,
                                    @RequestBody ValueRequest req) {
        service.updateValue(teamId, variableId, valueId,
                req.getValue(), req.getDescription(), req.getSortOrder());
        return Result.ok();
    }

    /**
     * DELETE /api/teams/{teamId}/variables/{id}/values/{vid} — 删除候选值
     */
    @DeleteMapping("/{id}/values/{vid}")
    public Result<Void> deleteValue(@PathVariable String teamId,
                                    @PathVariable("id") String variableId,
                                    @PathVariable("vid") String valueId) {
        service.deleteValue(teamId, variableId, valueId);
        return Result.ok();
    }

    // ==================== 分组 ====================

    /**
     * POST /api/teams/{teamId}/variables/{id}/groups — 创建分组（可带成员）
     */
    @PostMapping("/{id}/groups")
    public Result<CustomVariableDTO.GroupView> createGroup(@PathVariable String teamId,
                                                            @PathVariable("id") String variableId,
                                                            @RequestBody GroupRequest req) {
        return Result.ok(service.createGroup(teamId, variableId,
                req.getName(), req.getDescription(), req.getValueIds()));
    }

    /**
     * PUT /api/teams/{teamId}/variables/{id}/groups/{gid} — 编辑分组
     */
    @PutMapping("/{id}/groups/{gid}")
    public Result<Void> updateGroup(@PathVariable String teamId,
                                    @PathVariable("id") String variableId,
                                    @PathVariable("gid") String groupId,
                                    @RequestBody GroupRequest req) {
        service.updateGroup(teamId, variableId, groupId,
                req.getName(), req.getDescription(), req.getValueIds());
        return Result.ok();
    }

    /**
     * DELETE /api/teams/{teamId}/variables/{id}/groups/{gid} — 删除分组
     */
    @DeleteMapping("/{id}/groups/{gid}")
    public Result<Void> deleteGroup(@PathVariable String teamId,
                                    @PathVariable("id") String variableId,
                                    @PathVariable("gid") String groupId) {
        service.deleteGroup(teamId, variableId, groupId);
        return Result.ok();
    }
}
