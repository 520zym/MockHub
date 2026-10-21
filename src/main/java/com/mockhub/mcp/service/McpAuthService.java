package com.mockhub.mcp.service;

import com.mockhub.common.model.BizException;
import com.mockhub.mcp.model.McpToken;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.model.entity.UserTeam;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Bearer Token 鉴权：把 Token 对应用户写入 SecurityContext，
 * 复用现有 PermissionChecker / SecurityContextUtil。
 */
@Service
public class McpAuthService {

    private final McpTokenService tokenService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public McpAuthService(McpTokenService tokenService,
                          UserRepository userRepository,
                          TeamRepository teamRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    public McpToken authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(40100, "缺少 MCP Access Token");
        }
        McpToken token = tokenService.authenticate(header.substring("Bearer ".length()).trim());
        if (token == null) {
            throw new BizException(40101, "MCP Access Token 无效、已过期或已撤销");
        }
        User user = userRepository.findById(token.getUserId());
        if (user == null) {
            throw new BizException(40101, "Token 所属用户不存在");
        }
        bindSecurityContext(user);
        return token;
    }

    public void clear() {
        SecurityContextHolder.clearContext();
    }

    private void bindSecurityContext(User user) {
        List<String> teamIds = new ArrayList<String>();
        try {
            for (UserTeam relation : userRepository.findTeamRolesByUserId(user.getId())) {
                teamIds.add(relation.getTeamId());
            }
        } catch (Exception ignored) {
            // 团队关系读取失败时按无团队处理，后续接口会返回无权限
        }

        Map<String, Object> details = new HashMap<String, Object>();
        details.put("userId", user.getId());
        details.put("username", user.getUsername());
        details.put("globalRole", user.getGlobalRole());
        details.put("teamIds", teamIds);

        List<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getGlobalRole()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
        authentication.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
