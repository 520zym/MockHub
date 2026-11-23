package com.mockhub.system.service;

import com.mockhub.common.model.CurrentUser;
import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.common.model.enums.UserRole;
import com.mockhub.common.util.CurrentUserProvider;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.model.entity.UserTeam;
import com.mockhub.system.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 认证身份只取当前数据库事实，不缓存 JWT 签发时的角色和团队关系。
 */
@Service
public class CurrentUserProviderImpl implements CurrentUserProvider {

    private final UserRepository userRepository;

    private final AuthSessionRepository sessions;

    public CurrentUserProviderImpl(UserRepository userRepository, AuthSessionRepository sessions) {
        this.userRepository = userRepository;
        this.sessions = sessions;
    }

    @Override
    public CurrentUser findBySession(String userId, long authVersion, String sessionId) {
        CurrentUser current = findById(userId);
        if (current == null || current.getAuthVersion() != authVersion || sessionId == null
                || sessionId.trim().isEmpty()
                || !sessions.isActive(sessionId, userId, authVersion, System.currentTimeMillis())) {
            return null;
        }
        return current;
    }

    @Override
    public CurrentUser findById(String userId) {
        User user = userRepository.findById(userId);
        if (user == null || !user.isActive() || user.getAuthVersion() < 0 || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return null;
        }
        String globalRole = user.getGlobalRole();
        // 旧 schema 默认 MEMBER，也曾使用全局 TEAM_ADMIN；都只保留普通账号能力。
        // 兼容旧全局身份，但绝不能把团队身份升成全局管理权限。
        if ("MEMBER".equals(globalRole) || "TEAM_ADMIN".equals(globalRole)) {
            globalRole = UserRole.USER.name();
        }
        if (!(UserRole.USER.name().equals(globalRole)
                || UserRole.SUPER_ADMIN.name().equals(globalRole))) {
            return null;
        }

        Set<String> teamIds = new LinkedHashSet<String>();
        for (UserTeam relation : userRepository.findTeamRolesByUserId(userId)) {
            String teamId = relation.getTeamId();
            if (teamId != null && !teamId.isEmpty()) {
                teamIds.add(teamId);
            }
        }
        return new CurrentUser(user.getId(), user.getUsername(), globalRole,
                new ArrayList<String>(teamIds), user.getAuthVersion());
    }
}
