package com.mockhub.auth.service;

import com.mockhub.auth.repository.AuthSessionRepository;
import com.mockhub.common.model.BizException;
import com.mockhub.mcp.repository.McpTokenRepository;
import com.mockhub.system.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** Security-event primitive: always join the caller's transaction; never hide a revocation failure. */
@Service
public class CredentialRevocationService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final McpTokenRepository mcp;

    public CredentialRevocationService(UserRepository users, AuthSessionRepository sessions, McpTokenRepository mcp) {
        this.users = users; this.sessions = sessions; this.mcp = mcp;
    }

    /** Call in the same transaction as password/status mutation, including account reactivation. */
    @Transactional
    public void revokeSecurityCredentials(String userId) {
        String now = Instant.now().toString();
        if (users.incrementAuthVersion(userId, now) != 1) throw new BizException(40401, "用户不存在");
        sessions.revokeAllByUser(userId, now);
        mcp.revokeAllByOwner(userId, now);
    }
}
