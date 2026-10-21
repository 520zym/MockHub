package com.mockhub.ai.service;

import com.mockhub.common.config.DataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * API Key 静态加密：AES/GCM。
 * <p>
 * 密钥来源：环境变量 MOCKHUB_AI_KEY_SECRET（Base64 32 字节），否则 data/.ai-key 自动生成。
 * 密文格式：enc:v1:&lt;base64(iv + ciphertext)&gt;。旧明文数据兼容读取，由迁移组件加密回写。
 */
@Component
public class AiKeyCipher {

    private static final Logger log = LoggerFactory.getLogger(AiKeyCipher.class);

    public static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DataProperties dataProperties;
    private volatile SecretKeySpec key;

    public AiKeyCipher(DataProperties dataProperties) {
        this.dataProperties = dataProperties;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /**
     * 解密。旧明文（无 enc:v1: 前缀）原样返回，便于平滑迁移。
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败，请重新配置", e);
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec current = key;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (key == null) {
                key = loadOrCreateKey();
            }
            return key;
        }
    }

    private SecretKeySpec loadOrCreateKey() {
        String env = System.getenv("MOCKHUB_AI_KEY_SECRET");
        if (env != null && !env.trim().isEmpty()) {
            byte[] bytes = Base64.getDecoder().decode(env.trim());
            if (bytes.length != 32) {
                throw new IllegalStateException("MOCKHUB_AI_KEY_SECRET 必须是 Base64 编码的 32 字节密钥");
            }
            return new SecretKeySpec(bytes, "AES");
        }
        try {
            Path path = Paths.get(dataProperties.getPath(), ".ai-key");
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                byte[] bytes = new byte[32];
                RANDOM.nextBytes(bytes);
                Files.write(path, Base64.getEncoder().encode(bytes));
                restrictPermissions(path);
                log.info("已生成 API Key 加密密钥：{}", path.toAbsolutePath());
            }
            byte[] bytes = Base64.getDecoder().decode(new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim());
            if (bytes.length != 32) {
                throw new IllegalStateException("data/.ai-key 密钥长度不是 32 字节");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 API Key 加密密钥", e);
        }
    }

    private void restrictPermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>();
            permissions.add(PosixFilePermission.OWNER_READ);
            permissions.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (Exception ignored) {
            // Windows 等不支持 POSIX 权限时忽略
        }
    }
}
