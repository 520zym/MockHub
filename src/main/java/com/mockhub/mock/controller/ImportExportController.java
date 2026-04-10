package com.mockhub.mock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.Result;
import com.mockhub.mock.model.dto.ImportExportData;
import com.mockhub.mock.model.dto.ImportResult;
import com.mockhub.mock.service.ImportExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 接口导入导出 Controller
 * <p>
 * 提供接口数据的文件导入（multipart/form-data）和 JSON 文件导出（下载）端点。
 */
@RestController
@RequestMapping("/api/apis")
public class ImportExportController {

    private static final Logger log = LoggerFactory.getLogger(ImportExportController.class);

    private final ImportExportService importExportService;
    private final ObjectMapper objectMapper;

    public ImportExportController(ImportExportService importExportService,
                                  ObjectMapper objectMapper) {
        this.importExportService = importExportService;
        this.objectMapper = objectMapper;
    }

    /**
     * 导入接口
     * <p>
     * 接收 multipart/form-data 格式的请求，包含 JSON 文件、目标团队 ID 和导入模式。
     *
     * @param file   导出的 JSON 文件
     * @param teamId 导入到哪个团队
     * @param mode   导入模式：merge（合并）或 override（覆盖）
     * @return 导入结果统计
     */
    @PostMapping("/import")
    public Result<ImportResult> importApis(@RequestParam("file") MultipartFile file,
                                           @RequestParam("teamId") String teamId,
                                           @RequestParam(value = "mode", defaultValue = "merge") String mode) {
        if (file == null || file.isEmpty()) {
            throw new BizException(40401, "导入文件不能为空");
        }

        ImportExportData data;
        try {
            data = objectMapper.readValue(file.getInputStream(), ImportExportData.class);
        } catch (IOException e) {
            log.error("解析导入文件失败", e);
            throw new BizException(40401, "导入文件格式错误: " + e.getMessage());
        }

        ImportResult result = importExportService.importApis(teamId, data, mode);
        log.info("导入完成: teamId={}, mode={}, imported={}, skipped={}, overridden={}",
                teamId, mode, result.getImported(), result.getSkipped(), result.getOverridden());

        return Result.ok(result);
    }

    /**
     * 导出团队所有接口
     * <p>
     * 返回 JSON 文件下载（Content-Disposition: attachment）。
     *
     * @param teamId 团队 ID
     * @return JSON 文件下载响应
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportApis(@RequestParam String teamId) {
        ImportExportData data = importExportService.exportTeam(teamId);

        byte[] jsonBytes;
        try {
            jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (IOException e) {
            log.error("序列化导出数据失败", e);
            throw new BizException(50001, "导出数据序列化失败");
        }

        String fileName = "mockhub-export-" + teamId + ".json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        headers.setContentLength(jsonBytes.length);

        return ResponseEntity.ok().headers(headers).body(jsonBytes);
    }
}
