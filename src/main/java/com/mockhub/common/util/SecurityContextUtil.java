package com.mockhub.common.util;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 从 Spring SecurityContext 获取当前登录用户信息的工具类
 * <p>
 * JwtAuthFilter 校验通过后，会将用户信息存入 Authentication.details（Map 结构），
 * 包含：userId、username、globalRole、teamIds
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID
     * @throws BizException code=40002，未登录时抛出
     */
    public static String getCurrentUserId() {
        return getUserInfo().get("userId").toString();
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名
     * @throws BizException code=40002，未登录时抛出
     */
    public static String getCurrentUsername() {
        return getUserInfo().get("username").toString();
    }

    /**
     * 获取当前登录用户的全局角色
     *
     * @return 角色字符串（SUPER_ADMIN / USER）
     * @throws BizException code=40002，未登录时抛出
     */
    public static String getCurrentGlobalRole() {
        return getUserInfo().get("globalRole").toString();
    }

    /**
     * 获取当前登录用户所属团队 ID 列表
     *
     * @return 团队 ID 列表，无团队时返回空列表
     * @throws BizException code=40002，未登录时抛出
     */
    @SuppressWarnings("unchecked")
    public static List<String> getCurrentTeamIds() {
        Object teamIds = getUserInfo().get("teamIds");
        if (teamIds instanceof List) {
            return (List<String>) teamIds;
        }
        return Collections.emptyList();
    }

    /**
     * 判断当前用户是否为超级管理员
     *
     * @return 是超级管理员返回 true
     * @throws BizException code=40002，未登录时抛出
     */
    public static boolean isSuperAdmin() {
        return UserRole.SUPER_ADMIN.name().equals(getCurrentGlobalRole());
    }

    /**
     * 获取完整的用户信息 Map
     *
     * @return 包含 userId、username、globalRole、teamIds 的 Map
     * @throws BizException code=40002，未登录时抛出
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getDetails() == null) {
            throw new BizException(40002, "未登录");
        }

        Object details = authentication.getDetails();
        if (!(details instanceof Map)) {
            throw new BizException(40002, "未登录");
        }

        return (Map<String, Object>) details;
    }
}
