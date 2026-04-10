package com.mockhub.mock.controller;

import com.mockhub.common.model.Result;
import com.mockhub.mock.model.dto.WsdlParseResult;
import com.mockhub.mock.service.SoapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * SOAP/WSDL 管理 Controller
 * <p>
 * 提供三个端点：
 * <ul>
 *   <li>POST /api/soap/wsdl/upload — 上传 WSDL 文件（需认证）</li>
 *   <li>GET /api/soap/wsdl/{fileName}/operations — 解析操作列表（需认证）</li>
 *   <li>GET /wsdl/{fileName} — 托管 WSDL 文件（无需认证，不在 /api/ 下）</li>
 * </ul>
 */
@RestController
public class WsdlController {

    private static final Logger log = LoggerFactory.getLogger(WsdlController.class);

    private final SoapService soapService;

    public WsdlController(SoapService soapService) {
        this.soapService = soapService;
    }

    /**
     * 上传 WSDL 文件
     * <p>
     * 文件保存到 data/wsdl/ 目录，并解析返回操作列表。
     *
     * @param file 上传的 .wsdl 文件
     * @return 解析结果（文件名 + 操作列表）
     */
    @PostMapping("/api/soap/wsdl/upload")
    public Result<WsdlParseResult> uploadWsdl(@RequestParam("file") MultipartFile file) {
        WsdlParseResult result = soapService.uploadWsdl(file);
        return Result.ok(result);
    }

    /**
     * 重新解析已上传的 WSDL 文件，返回操作列表
     *
     * @param fileName WSDL 文件名
     * @return 解析结果
     */
    @GetMapping("/api/soap/wsdl/{fileName}/operations")
    public Result<WsdlParseResult> getOperations(@PathVariable String fileName) {
        WsdlParseResult result = soapService.parseOperations(fileName);
        return Result.ok(result);
    }

    /**
     * 托管 WSDL 文件（无需认证）
     * <p>
     * 返回 WSDL 文件内容，Content-Type 为 text/xml。
     * 动态替换 soap:address location 为当前服务器实际地址。
     *
     * @param fileName WSDL 文件名
     * @param request  原始请求（用于提取服务器地址）
     * @return WSDL 文件内容
     */
    @GetMapping("/wsdl/{fileName}")
    public ResponseEntity<String> serveWsdl(@PathVariable String fileName,
                                            HttpServletRequest request) {
        // 构建当前服务器地址（如 http://192.168.1.100:8080）
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String serverUrl = scheme + "://" + serverName;
        if (("http".equals(scheme) && serverPort != 80) ||
                ("https".equals(scheme) && serverPort != 443)) {
            serverUrl += ":" + serverPort;
        }

        String content = soapService.getWsdlContent(fileName, serverUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/xml; charset=UTF-8");

        return ResponseEntity.ok().headers(headers).body(content);
    }
}
