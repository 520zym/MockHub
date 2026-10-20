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

    public McpTokenService(McpTokenRepository repository) {
        this.repository = repository;
    }

    public McpTokenCreateResult create(String userId, McpTokenCreateRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BizException(40001, "请填写 Token 名称");
        }
        if (name.length() > 60) {
            throw new BizException(40001, "Token 名称不能超过 60 个字符");
        }
        if (request.getExpiresInDays() < 0) {
            throw new BizException(40001, "有效期不能为负数");
        }

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
        if (request.getExpiresInDays() > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, request.getExpiresInDays());
            token.setExpiresAt(format(calendar.getTime()));
        }
        repository.insert(token);

        McpTokenCreateResult result = new McpTokenCreateResult();
        result.setId(token.getId());
        result.setName(token.getName());
        result.setToken(raw);
        result.setTokenPrefix(token.getTokenPrefix());
        result.setExpiresAt(token.getExpiresAt());
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
        if (token == null || token.getRevokedAt() != null) {
            return null;
        }
        String now = now();
        if (token.getExpiresAt() != null && now.compareTo(token.getExpiresAt()) > 0) {
            return null;
        }
        repository.touchLastUsed(token.getId(), now);
        return token;
    }

    public List<McpTokenVO> list() {
        List<McpTokenVO> result = new ArrayList<McpTokenVO>();
        for (McpToken token : repository.findAllWithOwner()) {
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
            result.add(vo);
        }
        return result;
    }

    public void revoke(String id) {
        McpToken token = repository.findById(id);
        if (token == null) {
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