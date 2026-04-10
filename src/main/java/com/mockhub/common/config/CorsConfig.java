package com.mockhub.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 配置：
 * - mock.cors.enabled=true 时，/mock/** 路径允许所有跨域
 * - 管理接口 /api/** 不配置 CORS（开发时由 Vite 代理解决）
 */
@Configuration
public class CorsConfig {

    @Autowired
    private MockCorsProperties mockCorsProperties;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        if (mockCorsProperties.isEnabled()) {
            CorsConfiguration config = new CorsConfiguration();
            config.addAllowedOriginPattern("*");
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            config.setAllowCredentials(false);
            source.registerCorsConfiguration("/mock/**", config);
        }

        return new CorsFilter(source);
    }
}
