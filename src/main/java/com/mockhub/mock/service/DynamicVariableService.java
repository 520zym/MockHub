package com.mockhub.mock.service;

import com.mockhub.common.util.DynamicVariableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 动态变量替换 Service
 * <p>
 * 封装 {@link DynamicVariableUtil#replace(String, Map)} 调用，
 * 增加日志记录，方便排查占位符替换问题。
 */
@Service
public class DynamicVariableService {

    private static final Logger log = LoggerFactory.getLogger(DynamicVariableService.class);

    /**
     * 替换模板字符串中的所有动态变量占位符
     *
     * @param template      包含 {{xxx}} 占位符的模板字符串，可为 null
     * @param pathVariables 路径参数（如 {"id": "123"}），可为 null
     * @return 替换后的字符串，入参为 null 时返回 null
     */
    public String replace(String template, Map<String, String> pathVariables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        log.debug("开始动态变量替换, 模板长度={}, 路径参数={}", template.length(), pathVariables);
        String result = DynamicVariableUtil.replace(template, pathVariables);
        log.debug("动态变量替换完成, 结果长度={}", result.length());
        return result;
    }
}
