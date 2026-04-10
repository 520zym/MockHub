package com.mockhub.common.config;

import com.mockhub.common.filter.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（REST API 不需要）
            .csrf().disable()
            // 无状态 JWT，禁用 session
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                // Mock 请求路径，无需认证
                .antMatchers("/mock/**").permitAll()
                // WSDL 托管路径，无需认证
                .antMatchers("/wsdl/**").permitAll()
                // 健康检查，无需认证
                .antMatchers("/api/health").permitAll()
                // 登录接口，无需认证
                .antMatchers("/api/auth/login").permitAll()
                // 静态资源放行
                .antMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                // 其他 /api/** 需要认证
                .antMatchers("/api/**").authenticated()
                // 其他路径放行（前端路由等）
                .anyRequest().permitAll()
            .and()
            // 在 UsernamePasswordAuthenticationFilter 之前添加 JWT 过滤器
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
