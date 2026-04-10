package com.mockhub.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web MVC 配置
 * - StringHttpMessageConverter 默认编码设为 UTF-8，避免中文乱码
 * - multipart 限制已在 application.yml 中配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 将 StringHttpMessageConverter 的默认编码改为 UTF-8
     * Spring Boot 默认使用 ISO-8859-1，导致 ResponseEntity<String> 返回中文时乱码
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(c -> c instanceof StringHttpMessageConverter);
        converters.add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }
}
