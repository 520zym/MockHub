package com.mockhub.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类，使用 BCrypt 加密
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /**
     * 对原始密码进行 BCrypt 哈希
     *
     * @param raw 原始密码
     * @return BCrypt 哈希值
     */
    public static String hash(String raw) {
        return ENCODER.encode(raw);
    }

    /**
     * 校验原始密码是否匹配哈希值
     *
     * @param raw  原始密码
     * @param hash BCrypt 哈希值
     * @return 匹配返回 true
     */
    public static boolean verify(String raw, String hash) {
        return ENCODER.matches(raw, hash);
    }
}
