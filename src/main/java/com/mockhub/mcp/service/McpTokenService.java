package com.mockhub.mcp.service;

import com.mockhub.common.model.BizException;
import com.mockhub.mcp.model.McpToken;
import com.mockhub.mcp.model.dto.McpTokenCreateRequest;
import com.mockhub.mcp.model.dto.McpTokenCreateResult;
import com.mockhub.mcp.model.dto.McpTokenVO;
import com.mockhub.mcp.repository.McpTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * MCP 访问 Token：创建、校验、撤销。
 * <p>
 * 明文只在创建时返回一次；数据库只保存 SHA-256 哈希与展示前缀。
 */
@Service
public class McpTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "mhk_";

    private final McpTokenRepository repository;
    @org.springframework.beans.factory.annotation.Autowired
    private McpScopeService scopeService;

    public McpTokenService(McpTokenRepository repository) {
        this.repository = repository;
    }

    @org.springframework.transaction.annotation.Transactional
    public McpTokenCreateResult create(String userId, McpTokenCreateRequest request) {
        if (!userId.equals(com.mockhub.common.util.SecurityContextUtil.getCurrentUserId())) throw new BizException(40101, "只能为本人签发MCP凭证");
        if (request == null) throw new BizException(40001, "请求不能为空");
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BizException(40001, "请填写 Token 名称");
        }
        if (name.length() > 60) {
            throw new BizException(40001, "Token 名称不能超过 60 个字符");
        }
        if (request.getExpiresInDays() < 1 || request.getExpiresInDays() > 365) {
            throw new BizException(40001, "有效期须为1至365天，不签发永久凭证");
        }
        java.util.Map<String, java.util.Set<com.mockhub.mcp.model.McpScope>> scopes = scopeService.authorizeMint(userId, request);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        McpToken token = new McpToken();
        token.setId(UUID.randomUUID().toString());
        token.setName(name);
        token.setTokenHash(sha256(raw));
        token.setTokenPrefix(raw.substring(0, 12));
        token.setUserId(userId);
        token.setCreatedAt(now());
        token.setExpiresAt(java.time.Instant.now().plus(request.getExpiresInDays(), java.time.temporal.ChronoUnit.DAYS).toString());
        McpScopeCodec.encode(token, scopes);
        repository.insert(token);

        McpTokenCreateResult result = new McpTokenCreateResult();
        result.setId(token.getId());
        result.setName(token.getName());
        result.setToken(raw);
        result.setTokenPrefix(token.getTokenPrefix());
        result.setExpiresAt(token.getExpiresAt());
        result.setTeamIds(new java.util.ArrayList<String>(scopes.keySet()));
        result.setScopes(McpScopeCodec.union(scopes));
        result.setTeamScopes(McpScopeCodec.strings(scopes));
        return result;
    }

    /**
     * 校验明文 Token；失败返回 null，不区分具体原因，避免枚举。
     */
    public McpToken authenticate(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) {
            return null;
        }
        McpToken token = repository.findByHash(sha256(raw));
        if (!McpScopeCodec.isUsable(token)) return null;
        String now = now();
        repository.touchLastUsed(token.getId(), now);
        return token;
    }

    public List<McpTokenVO> list() {
        List<McpTokenVO> result = new ArrayList<McpTokenVO>();
        for (McpToken token : repository.findByOwner(com.mockhub.common.util.SecurityContextUtil.getCurrentUserId())) {
            McpTokenVO vo = new McpTokenVO();
            vo.setId(token.getId());
            vo.setName(token.getName());
            vo.setTokenPrefix(token.getTokenPrefix());
            vo.setUserId(token.getUserId());
            vo.setOwnerName(token.getOwnerName());
            vo.setCreatedAt(token.getCreatedAt());
            vo.setLastUsedAt(token.getLastUsedAt());
            vo.setExpiresAt(token.getExpiresAt());
            vo.setRevoked(token.getRevokedAt() != null);
            try {
                java.util.Map<String, java.util.Set<com.mockhub.mcp.model.McpScope>> scopes=McpScopeCodec.decode(token);
                vo.setTeamIds(new java.util.ArrayList<String>(scopes.keySet()));vo.setScopes(McpScopeCodec.union(scopes));vo.setTeamScopes(McpScopeCodec.strings(scopes));
            } catch (BizException invalid) { vo.setLegacy(true);vo.setRevoked(true); }
            result.add(vo);
        }
        return result;
    }

    public void revoke(String id) {
        McpToken token = repository.findById(id);
        if (token == null || !com.mockhub.common.util.SecurityContextUtil.getCurrentUserId().equals(token.getUserId())) {
            throw new BizException(40405, "Token 不存在");
        }
        if (token.getRevokedAt() != null) {
            return;
        }
        repository.revoke(id, now());
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String now() {
        return format(new Date());
    }

    private String format(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(date);
    }
}