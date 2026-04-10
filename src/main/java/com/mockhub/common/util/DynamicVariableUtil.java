package com.mockhub.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态变量替换工具类
 * <p>
 * 支持以下占位符：
 * - {{timestamp}} → 当前毫秒时间戳
 * - {{uuid}} → 随机 UUID
 * - {{date}} → 当前日期 yyyy-MM-dd
 * - {{datetime}} → 当前日期时间 yyyy-MM-dd HH:mm:ss
 * - {{random_int}} → 0~10000 随机整数
 * - {{path.xxx}} → 路径参数值
 */
public final class DynamicVariableUtil {

    /** 匹配 {{xxx}} 或 {{path.xxx}} 占位符 */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+(?:\\.\\w+)?)\\}\\}");

    private static final Random RANDOM = new Random();

    private DynamicVariableUtil() {
    }

    /**
     * 替换模板中的所有动态变量占位符
     *
     * @param template      包含占位符的模板字符串
     * @param pathVariables 路径参数，可为 null
     * @return 替换后的字符串
     */
    public static String replace(String template, Map<String, String> pathVariables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = resolve(placeholder, pathVariables);
            // Matcher.quoteReplacement 防止 replacement 中的 $ 和 \ 被特殊处理
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 解析单个占位符的值
     */
    private static String resolve(String placeholder, Map<String, String> pathVariables) {
        // 路径参数：path.xxx
        if (placeholder.startsWith("path.")) {
            String paramName = placeholder.substring(5);
            if (pathVariables != null && pathVariables.containsKey(paramName)) {
                return pathVariables.get(paramName);
            }
            // 未找到路径参数，保留原始占位符
            return "{{" + placeholder + "}}";
        }

        // 内置变量
        if ("timestamp".equals(placeholder)) {
            return String.valueOf(System.currentTimeMillis());
        }
        if ("uuid".equals(placeholder)) {
            return UUID.randomUUID().toString();
        }
        if ("date".equals(placeholder)) {
            return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        }
        if ("datetime".equals(placeholder)) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }
        if ("random_int".equals(placeholder)) {
            return String.valueOf(RANDOM.nextInt(10001));
        }

        // 未识别的占位符，保留原样
        return "{{" + placeholder + "}}";
    }
}
