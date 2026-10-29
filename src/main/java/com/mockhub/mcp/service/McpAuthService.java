package com.mockhub.mcp.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.CurrentUser;
import com.mockhub.common.util.CurrentUserProvider;
import com.mockhub.mcp.model.McpToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** MCP authenticates the token AND the owner's current active database identity on each call. */
@Service
public class McpAuthService {
    private final McpTokenService tokenService;
    private final CurrentUserProvider users;

    public McpAuthService(McpTokenService tokenService, CurrentUserProvider users) {
        this.tokenService = tokenService; this.users = users;
    }

    public McpToken authenticate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new BizException(40100, "缺少 MCP Access Token");
        McpToken token = tokenService.authenticate(header.substring("Bearer ".length()).trim());
        if (token == null) throw new BizException(40101, "MCP Access Token 无效、已过期或已撤销");
        CurrentUser user = users.findById(token.getUserId());
        if (user == null) throw new BizException(40101, "Token 所属账号不存在、已停用或身份无效");
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", user.getUserId()); details.put("username", user.getUsername());
        details.put("globalRole", user.getGlobalRole()); details.put("teamIds", user.getTeamIds());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUserId(), null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getGlobalRole())));
        authentication.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return token;
    }

    public void clear() { SecurityContextHolder.clearContext(); }
}
