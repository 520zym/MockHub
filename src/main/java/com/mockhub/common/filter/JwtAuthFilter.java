package com.mockhub.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.CurrentUser;
import com.mockhub.common.model.Result;
import com.mockhub.common.util.CurrentUserProvider;
import com.mockhub.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
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

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper, CurrentUserProvider currentUserProvider) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 放行路径不进入此 filter
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/mock/")
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
        // 请求之间不能沿用线程上已有的身份；失败或下游抛错时也必须清理。
        SecurityContextHolder.clearContext();
        try {
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                writeUnauthorized(response, "未登录");
                return;
            }

            String token = authHeader.substring(BEARER_PREFIX.length());
            Claims claims = jwtUtil.validateToken(token);
            if (claims == null) {
                writeUnauthorized(response, "Token 无效或已过期");
                return;
            }

            // JWT 只证明签发时的身份；全局角色、团队范围必须以当前数据库为准。
            String userId = claims.getSubject();
            if (userId == null || userId.trim().isEmpty()) {
                writeUnauthorized(response, "Token 无效或已过期");
                return;
            }
            CurrentUser user = currentUserProvider.findById(userId);
            if (user == null) {
                writeUnauthorized(response, "账号不存在或身份无效，请重新登录");
                return;
            }

            Map<String, Object> userInfo = new HashMap<String, Object>();
            userInfo.put("userId", user.getUserId());
            userInfo.put("username", user.getUsername());
            userInfo.put("globalRole", user.getGlobalRole());
            userInfo.put("teamIds", user.getTeamIds());

            List<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getGlobalRole()));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user.getUserId(), null, authorities);
            authentication.setDetails(userInfo);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 不把业务异常捕获成 401；数据库故障也不能回退到旧 claims。
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
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
