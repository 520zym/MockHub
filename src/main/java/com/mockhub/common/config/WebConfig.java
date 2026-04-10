package com.mockhub.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * multipart 限制已在 application.yml 中配置，此处预留扩展
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // 当前无需额外配置，multipart 限制已在 application.yml 中声明
}
