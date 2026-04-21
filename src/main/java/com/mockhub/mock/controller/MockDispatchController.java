package com.mockhub.mock.controller;

import com.mockhub.mock.service.MockDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Mock 请求分发入口 Controller
 * <p>
 * 监听 /mock/{teamIdentifier}/** 的所有 HTTP 方法请求，
 * 提取团队标识和请求路径后委托给 {@link MockDispatchService} 处理。
 * <p>
 * <b>注意</b>：
 * <ul>
 *   <li>此 Controller 不返回 {@code Result} 包装，直接返回 responseBody</li>
 *   <li>错误时返回 {@code {"error": "..."}} 格式</li>
 *   <li>此路径无需 JWT 认证，在 SecurityConfig 中放行</li>
 * </ul>
 */
@RestController
public class MockDispatchController {

    private static final Logger log = LoggerFactory.getLogger(MockDispatchController.class);

    private final MockDispatchService mockDispatchService;

    public MockDispatchController(MockDispatchService mockDispatchService) {
        this.mockDispatchService = mockDispatchService;
    }

    /**
     * 接收所有 Mock 请求
     * <p>
     * 匹配 /mock/{teamIdentifier}/ 后面的所有路径，
     * 从 request 中提取实际请求路径和 HTTP 方法。
     * <p>
     * <b>路由规则</b>：
     * <ul>
     *   <li>GET + query 含独立 wsdl 参数（不区分大小写）→ {@link MockDispatchService#serveWsdl} WSDL 托管</li>
     *   <li>其他情况 → {@link MockDispatchService#dispatch} 普通 Mock 分发</li>
     * </ul>
     *
     * @param teamIdentifier 团队短标识（如 "FE"、"BE"）
     * @param request        原始 HttpServletRequest
     * @return Mock 响应（直接返回配置的 responseBody）
     */
    @RequestMapping("/mock/{teamIdentifier}/**")
    public ResponseEntity<String> handleMockRequest(
            @PathVariable String teamIdentifier,
            HttpServletRequest request) {

        // 从 request 中提取 /mock/{teamIdentifier} 之后的路径部分
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = "/mock/" + teamIdentifier;
        String path = "";
        if (fullPath != null && fullPath.length() > prefix.length()) {
            path = fullPath.substring(prefix.length());
        }

        // 确保路径以 / 开头
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String method = request.getMethod().toUpperCase();

        // 方案 A：GET + 独立 wsdl 参数 → 走 WSDL 托管（和 .NET ASMX 风格一致）
        if ("GET".equals(method) && hasWsdlParam(request.getQueryString())) {
            log.debug("WSDL 托管请求: teamIdentifier={}, path={}", teamIdentifier, path);
            return mockDispatchService.serveWsdl(teamIdentifier, path, request);
        }

        log.debug("Mock 请求入口: teamIdentifier={}, method={}, path={}", teamIdentifier, method, path);
        return mockDispatchService.dispatch(teamIdentifier, method, path, request);
    }

    /**
     * 判断 query 里是否含独立的 wsdl 参数（不区分大小写）。
     * <p>
     * 命中：?wsdl / ?WSDL / ?wsdl= / ?foo=bar&amp;wsdl
     * 不命中：?foo=mywsdlvalue / ?xwsdl=1
     *
     * @param queryString 原始 query 字符串，可为 null
     * @return 是否命中独立 wsdl 参数
     */
    private boolean hasWsdlParam(String queryString) {
        if (queryString == null || queryString.isEmpty()) return false;
        for (String part : queryString.split("&")) {
            int eq = part.indexOf('=');
            String key = (eq < 0 ? part : part.substring(0, eq));
            if ("wsdl".equalsIgnoreCase(key)) return true;
        }
        return false;
    }
}
