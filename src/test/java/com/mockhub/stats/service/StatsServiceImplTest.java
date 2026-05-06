package com.mockhub.stats.service;

import com.mockhub.common.model.BizException;
import com.mockhub.stats.model.dto.StatsOverviewVO;
import com.mockhub.stats.model.dto.TrendPointVO;
import com.mockhub.stats.repository.StatsRepository;
import com.mockhub.system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatsServiceImpl 单元测试
 * <p>
 * 重点覆盖：
 * <ul>
 *   <li>权限分支：超管/团队管理员/普通成员的可见性边界</li>
 *   <li>时间窗口处理：days=0 / 负数 / 超过上限的兜底</li>
 *   <li>趋势数据补齐：稀疏数据点 → 连续 N 天（缺失日填 0）</li>
 * </ul>
 *
 * 通过手工构造 SecurityContext 模拟登录用户。
 */
class StatsServiceImplTest {

    private static final String SUPER_ADMIN_ID = "user-super";
    private static final String TEAM_ADMIN_ID = "user-team-admin";
    private static final String MEMBER_ID = "user-member";

    private static final String TEAM_A = "team-a";
    private static final String TEAM_B = "team-b";

    private StatsRepository statsRepository;
    private UserRepository userRepository;
    private StatsServiceImpl statsService;

    @BeforeEach
    void setUp() {
        statsRepository = mock(StatsRepository.class);
        userRepository = mock(UserRepository.class);
        statsService = new StatsServiceImpl(statsRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============ 权限：超管 ============

    @Test
    void getOverview_superAdmin_nullTeamId_shouldQueryCrossTeam() {
        loginAsSuperAdmin();
        when(statsRepository.countEnabledApis(isNull())).thenReturn(42L);
        when(statsRepository.countCalls(isNull(), anyString())).thenReturn(100L);
        when(statsRepository.avgDurationMs(isNull(), anyString())).thenReturn(15L);

        StatsOverviewVO vo = statsService.getOverview(null, 7);

        assertEquals(42L, vo.getEnabledApiCount());
        // 跨团队查询：repository 收到的 teamId 应为 null
        verify(statsRepository).countEnabledApis(isNull());
    }

    @Test
    void getOverview_superAdmin_specificTeamId_shouldPassThrough() {
        loginAsSuperAdmin();
        when(statsRepository.countEnabledApis(TEAM_A)).thenReturn(10L);
        when(statsRepository.countCalls(eq(TEAM_A), anyString())).thenReturn(50L);

        statsService.getOverview(TEAM_A, 7);

        verify(statsRepository).countEnabledApis(TEAM_A);
        // 超管不需要走 user_team 角色查询
        verify(userRepository, never()).findUserTeamRole(anyString(), anyString());
    }

    // ============ 权限：团队管理员 ============

    @Test
    void getOverview_teamAdmin_specificTeamId_shouldAllow() {
        loginAsRegularUser(TEAM_ADMIN_ID);
        when(userRepository.findUserTeamRole(TEAM_ADMIN_ID, TEAM_A)).thenReturn("TEAM_ADMIN");
        when(statsRepository.countEnabledApis(TEAM_A)).thenReturn(5L);

        StatsOverviewVO vo = statsService.getOverview(TEAM_A, 7);

        assertNotNull(vo);
        verify(userRepository).findUserTeamRole(TEAM_ADMIN_ID, TEAM_A);
        verify(statsRepository).countEnabledApis(TEAM_A);
    }

    @Test
    void getOverview_teamAdmin_nullTeamId_shouldReject() {
        loginAsRegularUser(TEAM_ADMIN_ID);

        BizException e = assertThrows(BizException.class,
                () -> statsService.getOverview(null, 7));
        assertEquals(40103, e.getCode());
        // 拒绝时不应触发任何仓储查询
        verify(statsRepository, never()).countEnabledApis(any());
    }

    @Test
    void getOverview_teamAdmin_otherTeamId_shouldReject() {
        loginAsRegularUser(TEAM_ADMIN_ID);
        // 用户在 TEAM_A 是 admin，但请求查询的是 TEAM_B
        when(userRepository.findUserTeamRole(TEAM_ADMIN_ID, TEAM_B)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> statsService.getOverview(TEAM_B, 7));
        assertEquals(40101, e.getCode());
        verify(statsRepository, never()).countEnabledApis(any());
    }

    // ============ 权限：普通成员 ============

    @Test
    void getOverview_member_anyTeamId_shouldReject() {
        loginAsRegularUser(MEMBER_ID);
        when(userRepository.findUserTeamRole(MEMBER_ID, TEAM_A)).thenReturn("MEMBER");

        BizException e = assertThrows(BizException.class,
                () -> statsService.getOverview(TEAM_A, 7));
        assertEquals(40101, e.getCode());
    }

    @Test
    void getOverview_member_nullTeamId_shouldReject() {
        loginAsRegularUser(MEMBER_ID);

        BizException e = assertThrows(BizException.class,
                () -> statsService.getOverview(null, 7));
        assertEquals(40103, e.getCode());
    }

    @Test
    void getZombies_member_shouldReject() {
        loginAsRegularUser(MEMBER_ID);
        when(userRepository.findUserTeamRole(MEMBER_ID, TEAM_A)).thenReturn("MEMBER");

        assertThrows(BizException.class,
                () -> statsService.getZombies(TEAM_A, 30, 20));
        verify(statsRepository, never()).zombieApis(any(), any(), anyInt());
    }

    // ============ 参数兜底：days / limit ============

    @Test
    void getTopApis_negativeDays_shouldClampToDefault7() {
        loginAsSuperAdmin();
        when(statsRepository.topApis(isNull(), anyString(), eq(10)))
                .thenReturn(Collections.emptyList());

        statsService.getTopApis(null, -5, 0);

        // days=-5 → 7 (default)；limit=0 → 10 (default)
        verify(statsRepository).topApis(isNull(), anyString(), eq(10));
    }

    @Test
    void getTopApis_oversizedLimit_shouldClampToMax100() {
        loginAsSuperAdmin();
        when(statsRepository.topApis(isNull(), anyString(), eq(100)))
                .thenReturn(Collections.emptyList());

        statsService.getTopApis(null, 7, 999);

        verify(statsRepository).topApis(isNull(), anyString(), eq(100));
    }

    // ============ 趋势数据补齐 ============

    @Test
    void getTrend_shouldFillMissingDaysWithZero() {
        loginAsSuperAdmin();
        // 仓储返回稀疏数据：仅 2 天有调用
        // 注：实际日期由 LocalDate.now 决定，这里只验证补齐结果长度与"0 填充"行为
        TrendPointVO p1 = new TrendPointVO(java.time.LocalDate.now().minusDays(2)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")), 100L);
        TrendPointVO p2 = new TrendPointVO(java.time.LocalDate.now().minusDays(0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")), 50L);
        when(statsRepository.aggregateDailyCalls(isNull(), anyString()))
                .thenReturn(Arrays.asList(p1, p2));

        List<TrendPointVO> result = statsService.getTrend(null, 7);

        assertEquals(7, result.size(), "应补齐为连续 7 天");
        // 缺失日填 0，已有日保留
        long zeroDays = result.stream().filter(p -> p.getCount() == 0L).count();
        long nonZeroDays = result.stream().filter(p -> p.getCount() > 0L).count();
        assertEquals(5, zeroDays);
        assertEquals(2, nonZeroDays);
    }

    @Test
    void getTrend_noData_shouldReturnAllZeros() {
        loginAsSuperAdmin();
        when(statsRepository.aggregateDailyCalls(isNull(), anyString()))
                .thenReturn(Collections.emptyList());

        List<TrendPointVO> result = statsService.getTrend(null, 7);

        assertEquals(7, result.size());
        for (TrendPointVO p : result) {
            assertEquals(0L, p.getCount());
        }
    }

    // ============ 辅助：构造 SecurityContext ============

    private void loginAsSuperAdmin() {
        login(SUPER_ADMIN_ID, "super", "SUPER_ADMIN", Collections.<String>emptyList());
    }

    private void loginAsRegularUser(String userId) {
        login(userId, userId, "USER", Arrays.asList(TEAM_A));
    }

    private void login(String userId, String username, String globalRole, List<String> teamIds) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("username", username);
        details.put("globalRole", globalRole);
        details.put("teamIds", teamIds);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                username, null, Collections.emptyList());
        ((UsernamePasswordAuthenticationToken) auth).setDetails(details);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // 引用以避免 IDE 警告：anyInt 用于 limit 参数
    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
