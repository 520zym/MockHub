package com.mockhub.system.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查与服务器信息控制器
 * <p>
 * 提供无需认证的端点：健康检查、服务器地址获取。
 */
@RestController
public class HealthController {

    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * 用户手动配置的服务器地址（为空时自动检测）。
     * 通过 SettingsController 在内存中修改。
     */
    private volatile String customServerAddress = null;

    @GetMapping("/api/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("status", "UP");
        result.put("version", "1.0.0");
        return result;
    }

    /**
     * GET /api/server-address — 获取 Mock 服务的完整基础地址
     * <p>
     * 无需认证，供前端拼接 Mock URL。
     * 优先返回手动配置的地址，否则自动检测内网 IP。
     *
     * @return {"address": "http://192.168.1.100:8080"}
     */
    @GetMapping("/api/server-address")
    public Map<String, String> serverAddress() {
        Map<String, String> result = new HashMap<String, String>();
        if (customServerAddress != null && !customServerAddress.isEmpty()) {
            result.put("address", customServerAddress);
        } else {
            result.put("address", "http://" + detectInternalIp() + ":" + serverPort);
        }
        return result;
    }

    /** 供 SettingsController 调用设置自定义地址 */
    public void setCustomServerAddress(String address) {
        this.customServerAddress = address;
    }

    /** 获取当前自定义地址 */
    public String getCustomServerAddress() {
        return this.customServerAddress;
    }

    /**
     * 检测内网 IP 地址
     * <p>
     * 遍历网络接口，找到第一个非回环的 IPv4 地址。
     * 找不到时返回 localhost。
     */
    private String detectInternalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "localhost";
    }
}
