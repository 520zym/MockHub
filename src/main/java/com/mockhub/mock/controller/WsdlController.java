package com.mockhub.mock.controller;

import com.mockhub.common.model.Result;
import com.mockhub.mock.model.dto.WsdlParseResult;
import com.mockhub.mock.service.SoapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * SOAP/WSDL 管理 Controller（管理端）
 * <p>
 * 仅保留 WSDL 上传和解析端点，需要 JWT 认证。
 * WSDL 托管已迁移到 MockDispatchController 的 GET ?wsdl 分支（方案 A），
 * 支持 {@code GET /mock/{team}/{path}?wsdl} 返回动态替换 location 的 WSDL。
 */
@RestController
public class WsdlController {

    private static final Logger log = LoggerFactory.getLogger(WsdlController.class);

    private final SoapService soapService;

    public WsdlController(SoapService soapService) {
        this.soapService = soapService;
    }

    /**
     * 上传 WSDL 文件并解析操作列表。
     *
     * @param file 上传的 .wsdl 文件
     * @return 解析结果（文件名 + 操作列表 + 骨架）
     */
    @PostMapping("/api/soap/wsdl/upload")
    public Result<WsdlParseResult> uploadWsdl(@RequestParam("file") MultipartFile file) {
        WsdlParseResult result = soapService.uploadWsdl(file);
        return Result.ok(result);
    }

    /**
     * 重新解析已上传的 WSDL 文件，返回操作列表。
     *
     * @param fileName WSDL 文件名
     * @return 解析结果
     */
    @GetMapping("/api/soap/wsdl/{fileName}/operations")
    public Result<WsdlParseResult> getOperations(@PathVariable String fileName) {
        WsdlParseResult result = soapService.parseOperations(fileName);
        return Result.ok(result);
    }
}
