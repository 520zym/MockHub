package com.mockhub.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.Result;
import com.mockhub.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 认证过滤器：
 * - 从 Authorization: Bearer {token} 头提取 token
 * - 校验成功：将用户信息放入 SecurityContext
 * - 校验失败：直接写 HTTP 401 响应
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 放行路径不进入此 filter
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/mock/")
                || path.startsWith("/wsdl/")
                || path.equals("/api/health")
                || path.equals("/api/server-address")
                || path.equals("/api/auth/login")
                || path.equals("/")
                || path.equals("/index.html")
                || path.startsWith("/assets/")
                || path.equals("/favicon.ico")
                || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // 无 Token
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "未登录");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        Claims claims = jwtUtil.validateToken(token);

        // Token 无效或已过期
        if (claims == null) {
            writeUnauthorized(response, "Token 无效或已过期");
            return;
        }

        // 从 Claims 中提取用户信息，放入 SecurityContext
        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        String globalRole = claims.get("globalRole", String.class);
        String teamIdsStr = claims.get("teamIds", String.class);

        List<String> teamIds = new ArrayList<String>();
        if (teamIdsStr != null && !teamIdsStr.isEmpty()) {
            teamIds = Arrays.asList(teamIdsStr.split(","));
        }

        // 构建用户信息 Map 存入 Authentication 的 details
        Map<String, Object> userInfo = new HashMap<String, Object>();
        userInfo.put("userId", userId);
        userInfo.put("username", username);
        userInfo.put("globalRole", globalRole);
        userInfo.put("teamIds", teamIds);

        // 创建 Authentication，principal 为 userId，authorities 包含角色
        List<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + globalRole));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authentication.setDetails(userInfo);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * 直接写 401 响应，不进入 Controller 层
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<?> result = Result.error(40002, msg);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
