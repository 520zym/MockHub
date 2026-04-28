package com.mockhub.mock.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GroupService 单元测试
 * <p>
 * 重点覆盖：
 *  - 写操作（create / update / delete）的权限校验从 checkTeamAccess
 *    收紧为 checkTeamAdmin，普通成员调用应被 PermissionChecker 拦截，
 *    且后续仓储不会被调用（即权限校验先于业务逻辑）。
 *  - 读操作（findByTeamId）保持 checkTeamAccess，且会填充每个分组的 apiCount。
 *  - 删除分组时先把关联接口的 group_id 置 null，再删分组本体。
 */
class GroupServiceTest {

    private GroupRepository groupRepository;
    private ApiRepository apiRepository;
    private PermissionChecker permissionChecker;
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        apiRepository = mock(ApiRepository.class);
        permissionChecker = mock(PermissionChecker.class);
        groupService = new GroupService(groupRepository, apiRepository, permissionChecker);
    }

    // ============ 读：findByTeamId ============

    @Test
    void findByTeamId_shouldUseTeamAccess_andFillApiCount() {
        // 准备两个分组，仓储返回这两个，分别有 3 / 0 个接口
        ApiGroup g1 = newGroup("g-1", "team-1", "用户模块", 1);
        ApiGroup g2 = newGroup("g-2", "team-1", "订单模块", 2);
        when(groupRepository.findByTeamId("team-1")).thenReturn(Arrays.asList(g1, g2));
        when(apiRepository.countByGroupId("g-1")).thenReturn(3L);
        when(apiRepository.countByGroupId("g-2")).thenReturn(0L);

        List<ApiGroup> result = groupService.findByTeamId("team-1");

        assertEquals(2, result.size());
        assertEquals(3, result.get(0).getApiCount());
        assertEquals(0, result.get(1).getApiCount());
        // 读操作走的是访问权限校验，而不是管理员校验
        verify(permissionChecker, times(1)).checkTeamAccess("team-1");
        verify(permissionChecker, never()).checkTeamAdmin(anyString());
    }

    @Test
    void findByTeamId_shouldRejectWhenNoAccess() {
        doThrow(new BizException(40102, "无权访问")).when(permissionChecker).checkTeamAccess("team-1");

        assertThrows(BizException.class, () -> groupService.findByTeamId("team-1"));
        // 权限失败时不应触发任何仓储调用
        verify(groupRepository, never()).findByTeamId(anyString());
        verify(apiRepository, never()).countByGroupId(anyString());
    }

    // ============ 写：create —— 仅团队管理员可执行 ============

    @Test
    void create_shouldRejectNonAdmin() {
        ApiGroup input = newGroup(null, "team-1", "新分组", 1);
        // 模拟普通成员：checkTeamAdmin 抛出 40101
        doThrow(new BizException(40101, "无操作权限"))
                .when(permissionChecker).checkTeamAdmin("team-1");

        BizException ex = assertThrows(BizException.class, () -> groupService.create(input));
        assertEquals(40101, ex.getCode());
        // 权限拒绝后，仓储不应被调用
        verify(groupRepository, never()).insert(any());
    }

    @Test
    void create_shouldGenerateIdAndPersist_whenAdmin() {
        ApiGroup input = newGroup(null, "team-1", "支付模块", 5);
        doNothing().when(permissionChecker).checkTeamAdmin("team-1");

        ApiGroup created = groupService.create(input);

        assertNotNull(created.getId(), "应生成 UUID");
        assertNotNull(created.getCreatedAt(), "应填充创建时间");
        assertEquals("支付模块", created.getName());
        assertEquals("team-1", created.getTeamId());
        verify(groupRepository, times(1)).insert(created);
    }

    // ============ 写：update —— 仅团队管理员可执行 ============

    @Test
    void update_shouldRejectNonAdmin() {
        ApiGroup existing = newGroup("g-1", "team-1", "旧名", 1);
        when(groupRepository.findById("g-1")).thenReturn(existing);
        doThrow(new BizException(40101, "无操作权限"))
                .when(permissionChecker).checkTeamAdmin("team-1");

        ApiGroup patch = newGroup(null, null, "新名", 9);

        assertThrows(BizException.class, () -> groupService.update("g-1", patch));
        verify(groupRepository, never()).update(any());
    }

    @Test
    void update_shouldThrow_whenGroupMissing() {
        when(groupRepository.findById("missing")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> groupService.update("missing", newGroup(null, null, "x", 1)));
        assertEquals(40501, ex.getCode());
        // 分组不存在的早返回路径不触发权限校验
        verify(permissionChecker, never()).checkTeamAdmin(anyString());
    }

    @Test
    void update_shouldPersistNameAndOrder_whenAdmin() {
        ApiGroup existing = newGroup("g-1", "team-1", "旧名", 1);
        when(groupRepository.findById("g-1")).thenReturn(existing);
        doNothing().when(permissionChecker).checkTeamAdmin("team-1");

        ApiGroup patch = newGroup(null, null, "新名", 9);
        ApiGroup result = groupService.update("g-1", patch);

        assertEquals("新名", result.getName());
        assertEquals(9, result.getSortOrder());
        verify(groupRepository, times(1)).update(existing);
    }

    // ============ 写：delete —— 仅团队管理员可执行 ============

    @Test
    void delete_shouldRejectNonAdmin() {
        ApiGroup existing = newGroup("g-1", "team-1", "x", 1);
        when(groupRepository.findById("g-1")).thenReturn(existing);
        doThrow(new BizException(40101, "无操作权限"))
                .when(permissionChecker).checkTeamAdmin("team-1");

        assertThrows(BizException.class, () -> groupService.delete("g-1"));
        // 拒绝后既不清理关联接口，也不删分组
        verify(apiRepository, never()).clearGroupId(anyString());
        verify(groupRepository, never()).deleteById(anyString());
    }

    @Test
    void delete_shouldClearApiGroupIdBeforeDeleting_whenAdmin() {
        ApiGroup existing = newGroup("g-1", "team-1", "x", 1);
        when(groupRepository.findById("g-1")).thenReturn(existing);
        doNothing().when(permissionChecker).checkTeamAdmin("team-1");

        groupService.delete("g-1");

        // 顺序很关键：先把接口的 groupId 置 null，再删分组本体
        // （否则若先删分组，外键/孤儿数据可能让接口带着已不存在的 groupId）
        verify(apiRepository, times(1)).clearGroupId("g-1");
        verify(groupRepository, times(1)).deleteById("g-1");
    }

    @Test
    void delete_shouldThrow_whenGroupMissing() {
        when(groupRepository.findById("missing")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> groupService.delete("missing"));
        assertEquals(40501, ex.getCode());
        verify(permissionChecker, never()).checkTeamAdmin(anyString());
    }

    // ============ 边界 ============

    @Test
    void findByTeamId_shouldReturnEmpty_whenNoGroups() {
        when(groupRepository.findByTeamId("team-empty")).thenReturn(Collections.emptyList());

        List<ApiGroup> result = groupService.findByTeamId("team-empty");

        assertEquals(0, result.size());
        // 没有分组时不应再调 countByGroupId
        verify(apiRepository, never()).countByGroupId(anyString());
    }

    // ============ Helper ============

    private ApiGroup newGroup(String id, String teamId, String name, int sortOrder) {
        ApiGroup g = new ApiGroup();
        g.setId(id);
        g.setTeamId(teamId);
        g.setName(name);
        g.setSortOrder(sortOrder);
        return g;
    }
}
