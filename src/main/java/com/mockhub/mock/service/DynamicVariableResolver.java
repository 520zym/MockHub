package com.mockhub.mock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态变量解析器
 * <p>
 * 编排内置变量、路径参数、自定义变量三类占位符的解析。保留原
 * {@link com.mockhub.common.util.DynamicVariableUtil} 向后兼容，
 * 其他调用方（如 GlobalHeaderService）继续用 util。
 * <p>
 * 占位符语法与 util 一致：{@code \{\{(\w+(?:\.\w+)?)\}\}}，
 * 但本 resolver 增加了自定义变量的处理。
 * <p>
 * 解析优先级：
 * <ol>
 *   <li>{@code {{path.xxx}}} → 走路径参数（缺失保留原样，与现有行为一致）</li>
 *   <li>单段 {@code {{xxx}}}：先查内置（timestamp/uuid/date/datetime/random_int）
 *       → 命中返回；未命中查自定义变量 → 命中走随机挑选；都未命中保留原样</li>
 *   <li>两段 {@code {{xxx.yyy}}}：查团队的自定义变量
 *       <ul>
 *         <li>变量存在且分组存在且分组非空 → 从分组随机挑选</li>
 *         <li>变量存在但分组不存在 / 分组为空 → 抛
 *             {@link com.mockhub.common.exception.UnresolvedPlaceholderException}
 *             （Fail-fast，仅此一种场景）</li>
 *         <li>变量不存在 → 保留原样</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Service
public class DynamicVariableResolver {

    private static final Logger log = LoggerFactory.getLogger(DynamicVariableResolver.class);

    /** 匹配 {{xxx}} 或 {{xxx.yyy}} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+(?:\\.\\w+)?)\\}\\}");

    private static final Random RANDOM = new Random();

    private final CustomVariableService customVariableService;

    @Autowired
    public DynamicVariableResolver(CustomVariableService customVariableService) {
        this.customVariableService = customVariableService;
    }

    /**
     * 解析模板中全部占位符
     *
     * @param template       响应体模板
     * @param teamId         所属团队 ID
     * @param teamIdentifier 团队短标识（用于错误消息）
     * @param pathVariables  路径参数键值，可为 null
     * @return 替换后的字符串；如遇无法解析的自定义分组占位符则抛
     *         {@link com.mockhub.common.exception.UnresolvedPlaceholderException}
     */
    public String resolve(String template, String teamId, String teamIdentifier,
                          Map<String, String> pathVariables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = resolveOne(placeholder, teamId, teamIdentifier, pathVariables);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 解析单个占位符
     */
    private String resolveOne(String placeholder, String teamId, String teamIdentifier,
                              Map<String, String> pathVariables) {
        // 1. 路径参数
        if (placeholder.startsWith("path.")) {
            String paramName = placeholder.substring(5);
            if (pathVariables != null && pathVariables.containsKey(paramName)) {
                return pathVariables.get(paramName);
            }
            // 未找到：保留原样
            return "{{" + placeholder + "}}";
        }

        // 2. 两段 {{xxx.yyy}}
        int dotIndex = placeholder.indexOf('.');
        if (dotIndex >= 0) {
            String varName = placeholder.substring(0, dotIndex);
            String groupName = placeholder.substring(dotIndex + 1);
            // 先判断变量是否存在（不存在则保留原样，向后兼容）
            if (!customVariableService.hasVariable(teamId, varName)) {
                return "{{" + placeholder + "}}";
            }
            // 变量存在 → 从分组挑选。若分组不存在或空，randomPickValue 会抛 UnresolvedPlaceholderException
            String picked = customVariableService.randomPickValue(teamId, teamIdentifier, varName, groupName);
            return picked != null ? picked : "{{" + placeholder + "}}";
        }

        // 3. 单段 {{xxx}}
        // 3a. 先查内置
        String builtIn = resolveBuiltIn(placeholder);
        if (builtIn != null) {
            return builtIn;
        }
        // 3b. 查自定义变量（单段 = 从全部值随机挑）
        if (customVariableService.hasVariable(teamId, placeholder)) {
            String picked = customVariableService.randomPickValue(teamId, teamIdentifier, placeholder, null);
            if (picked != null) {
                return picked;
            }
        }
        // 3c. 都不命中：保留原样
        return "{{" + placeholder + "}}";
    }

    /**
     * 内置变量求值，命中返回字符串，未命中返回 null
     * <p>
     * 与 {@link com.mockhub.common.util.DynamicVariableUtil} 保持一致。此处复刻一份
     * 是为了让 resolver 自包含，不依赖 util 的内部 private 方法。util 仍为
     * GlobalHeaderService 等其他调用方保留。
     */
    private String resolveBuiltIn(String name) {
        if ("timestamp".equals(name)) {
            return String.valueOf(System.currentTimeMillis());
        }
        if ("uuid".equals(name)) {
            return UUID.randomUUID().toString();
        }
        if ("date".equals(name)) {
            return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        }
        if ("datetime".equals(name)) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }
        if ("random_int".equals(name)) {
            return String.valueOf(RANDOM.nextInt(10001));
        }
        return null;
    }
}
