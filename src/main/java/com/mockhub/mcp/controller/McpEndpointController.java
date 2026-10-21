package com.mockhub.mcp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.common.model.BizException;
import com.mockhub.mcp.model.McpSettings;
import com.mockhub.mcp.model.McpToken;
import com.mockhub.mcp.service.McpAuthService;
import com.mockhub.mcp.service.McpServerService;
import com.mockhub.mcp.service.McpSettingsService;
import com.mockhub.system.controller.HealthController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * MCP Streamable HTTP 端点：POST /mcp 接收 JSON-RPC，GET /mcp 返回 405。
 * <p>
 * 不依赖 JwtAuthFilter（非 /api 路径），使用 MCP Bearer Token 独立鉴权。
 */
@RestController
public class McpEndpointController {

    private static final Logger log = LoggerFactory.getLogger(McpEndpointController.class);

    private final ObjectMapper objectMapper;
    private final McpAuthService authService;
    private final McpSettingsService settingsService;
    private final McpServerService serverService;
    private final HealthController healthController;

    public McpEndpointController(ObjectMapper objectMapper,
                                 McpAuthService authService,
                                 McpSettingsService settingsService,
                                 McpServerService serverService,
                                 HealthController healthController) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.settingsService = settingsService;
        this.serverService = serverService;
        this.healthController = healthController;
    }

    @PostMapping(value = "/mcp", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> post(@RequestBody(required = false) String body,
                                       HttpServletRequest request) {
        if (!originAllowed(request)) {
            return json(HttpStatus.FORBIDDEN, rpcError(-32000, "Origin not allowed"));
        }

        McpToken token;
        try {
            token = authService.authenticate(request);
        } catch (BizException e) {
            return json(HttpStatus.UNAUTHORIZED, rpcError(-32001, e.getMsg()));
        }
        try {
            McpSettings settings = settingsService.load();
            if (!settings.isEnabled()) {
                return json(HttpStatus.FORBIDDEN, rpcError(-32001, "MCP Server 未启用"));
            }
            JsonNode requestJson;
            try {
                requestJson = objectMapper.readTree(body == null ? "" : body);
            } catch (Exception e) {
                return json(HttpStatus.BAD_REQUEST, rpcError(-32700, "Parse error"));
            }
            ObjectNode response = serverService.handle(requestJson, token);
            if (response == null) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).build();
            }
            return json(HttpStatus.OK, response.toString());
        } catch (BizException e) {
            log.warn("MCP 请求失败：{}", e.getMsg());
            return json(HttpStatus.FORBIDDEN, rpcError(-32000, e.getMsg()));
        } catch (Exception e) {
            log.error("MCP 请求处理失败", e);
            return json(HttpStatus.INTERNAL_SERVER_ERROR, rpcError(-32603, "Internal error"));
        } finally {
            authService.clear();
        }
    }

    @GetMapping("/mcp")
    public ResponseEntity<String> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Allow", "POST")
                .contentType(MediaType.APPLICATION_JSON)
                .body(rpcError(-32000, "SSE stream is not supported; use POST"));
    }

    private boolean originAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.trim().isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String originHost = uri.getHost();
            if (originHost == null) {
                return false;
            }
            if (originHost.equalsIgnoreCase(request.getServerName())) {
                return true;
            }
            String configured = findConfiguredAddress();
            if (configured != null) {
                String host = URI.create(configured).getHost();
                return host != null && host.equalsIgnoreCase(originHost);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String findConfiguredAddress() {
        try {
            return healthController.serverAddress().get("address");
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<String> json(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private String rpcError(int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.putNull("id");
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response.toString();
    }
}