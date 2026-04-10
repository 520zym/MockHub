package com.mockhub.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * <p>
 * 提供无需认证的健康检查端点，返回服务状态和版本信息。
 * 直接返回 JSON 对象，不包装 Result 格式。
 */
@RestController
public class HealthController {

    /**
     * GET /api/health — 健康检查
     *
     * @return {"status":"UP","version":"1.0.0"}
     */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("status", "UP");
        result.put("version", "1.0.0");
        return result;
    }
}
