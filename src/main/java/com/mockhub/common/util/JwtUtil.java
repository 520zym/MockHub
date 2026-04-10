package com.mockhub.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类：
 * - 启动时生成随机密钥（HMAC-SHA256）
 * - Token 有效期 8 小时
 * - 存储 userId（subject）、username、globalRole、teamIds
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** Token 有效期：8 小时（毫秒） */
    private static final long EXPIRATION_MS = 8 * 60 * 60 * 1000L;

    private Key signingKey;

    @PostConstruct
    public void init() {
        // 启动时生成 256 位随机密钥
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        this.signingKey = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
        log.info("JWT 签名密钥已生成");
    }

    /**
     * 生成 JWT Token
     *
     * @param userId     用户 ID（存入 subject）
     * @param username   用户名
     * @param globalRole 全局角色
     * @param teamIds    用户所属团队 ID 列表
     * @return JWT Token 字符串
     */
    public String generateToken(String userId, String username, String globalRole, List<String> teamIds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        // 将 teamIds 用逗号拼接存储
        String teamIdsStr = "";
        if (teamIds != null && !teamIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < teamIds.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(teamIds.get(i));
            }
            teamIdsStr = sb.toString();
        }

        return Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .claim("globalRole", globalRole)
                .claim("teamIds", teamIdsStr)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS256, signingKey)
                .compact();
    }

    /**
     * 校验 Token 并返回 Claims
     *
     * @param token JWT Token 字符串
     * @return 解析后的 Claims，无效或过期时返回 null
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(signingKey)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.debug("JWT 校验失败：{}", e.getMessage());
            return null;
        }
    }
}
