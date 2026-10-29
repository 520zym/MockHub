package com.mockhub.mock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.Result;
import com.mockhub.mock.model.dto.ImportExportData;
import com.mockhub.mock.model.dto.ImportResult;
import com.mockhub.mock.service.ImportExportService;
import org.springframework.core.io.Resource;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
    @org.springframework.beans.factory.annotation.Value("${mock.import.max-bytes:104857600}")
    private long maxImportBytes=104857600L;

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
                                           @RequestParam(value = "mode", defaultValue = "merge") String mode,
                                           @RequestParam(value="expectedRevisions",required=false) String expectedRevisions) {
        importExportService.checkImportPermission(teamId,mode);
        if (file == null || file.isEmpty()) {
            throw new BizException(40401, "导入文件不能为空");
        }
        if(file.getSize()>maxImportBytes)throw new BizException(40000,"导入包过大");

        ImportExportData data;
        Map<String, ImportExportService.FileBundleEntry> bundledFiles = new HashMap<String, ImportExportService.FileBundleEntry>();
        try {
            if (isZipFile(file)) {
                ImportBundle bundle = readImportBundle(file);
                data = bundle.getData();
                bundledFiles = bundle.getFiles();
            } else {
                data = objectMapper.readValue(file.getInputStream(), ImportExportData.class);
            }
        } catch (IOException e) {
            log.error("解析导入文件失败", e);
            throw new BizException(40401, "导入文件格式错误: " + e.getMessage());
        }

        Map<String,Long> versions=java.util.Collections.emptyMap();
        if(expectedRevisions!=null&&!expectedRevisions.trim().isEmpty())try {
            versions=objectMapper.readValue(expectedRevisions,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Long>>(){});
        } catch(IOException invalid) { throw new BizException(40000,"expectedRevisions格式错误，请刷新版本后重试"); }
        ImportResult result = importExportService.importApis(teamId, data, mode, bundledFiles,versions);
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
    public ResponseEntity<byte[]> exportApis(@RequestParam String teamId,
                                             @RequestParam(required = false) List<String> ids) {
        ImportExportService.ExportPackage exportPackage = importExportService.exportApisPackage(teamId, ids);
        ImportExportData data = exportPackage.getData();

        byte[] jsonBytes;
        try {
            jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (IOException e) {
            log.error("序列化导出数据失败", e);
            throw new BizException(50001, "导出数据序列化失败");
        }

        String extension = exportPackage.hasFiles() ? ".zip" : ".json";
        String fileName = "mockhub-export-" + teamId + extension;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(exportPackage.hasFiles() ? MediaType.parseMediaType("application/zip") : MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");

        byte[] body = exportPackage.hasFiles() ? buildZip(jsonBytes, exportPackage.getFiles()) : jsonBytes;
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private boolean isZipFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".zip");
    }

    private ImportBundle readImportBundle(MultipartFile file) throws IOException {
        ImportExportData data = null;
        Map<String, ImportExportService.FileBundleEntry> files =
                new HashMap<String, ImportExportService.FileBundleEntry>();
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;long total=0;int count=0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if(++count>1000)throw new BizException(40000,"导入包条目过多");
                String entryName = entry.getName();
                if(entryName.startsWith("/")||entryName.contains("../")||entryName.endsWith("/..")||entryName.indexOf('\\')>=0)
                    throw new BizException(40000,"导入包包含非法文件路径");
                byte[] bytes = readAll(zipInputStream);total+=bytes.length;
                if(total>maxImportBytes)throw new BizException(40000,"导入包解压后过大");
                if ("mockhub-export.json".equals(entryName)) {
                    if(data!=null)throw new BizException(40000,"导入包重复定义主JSON文件");
                    data = objectMapper.readValue(bytes, ImportExportData.class);
                } else if (entryName.startsWith("files/")) {
                    String relativePath = entryName.substring("files/".length());
                    String fileName = relativePath;
                    int slash = relativePath.lastIndexOf('/');
                    if (slash >= 0) {
                        fileName = relativePath.substring(slash + 1);
                    }
                    if(files.containsKey(relativePath))throw new BizException(40000,"导入包重复定义附件");
                    files.put(relativePath, new ImportExportService.FileBundleEntry(fileName, bytes));
                }
            }
        }
        if (data == null) {
            throw new BizException(40401, "导入包缺少 mockhub-export.json");
        }
        return new ImportBundle(data, files);
    }

    private byte[] buildZip(byte[] jsonBytes, Map<String, Resource> files) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                zipOutputStream.putNextEntry(new ZipEntry("mockhub-export.json"));
                zipOutputStream.write(jsonBytes);
                zipOutputStream.closeEntry();
                for (Map.Entry<String, Resource> entry : files.entrySet()) {
                    zipOutputStream.putNextEntry(new ZipEntry("files/" + entry.getKey()));
                    try (InputStream inputStream = entry.getValue().getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            zipOutputStream.write(buffer, 0, len);
                        }
                    }
                    zipOutputStream.closeEntry();
                }
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("生成导出包失败", e);
            throw new BizException(50001, "生成导出包失败");
        }
    }

    private byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            if((long)outputStream.size()+len>maxImportBytes)throw new BizException(40000,"导入包条目过大");
            outputStream.write(buffer, 0, len);
        }
        return outputStream.toByteArray();
    }

    private static class ImportBundle {
        private final ImportExportData data;
        private final Map<String, ImportExportService.FileBundleEntry> files;

        ImportBundle(ImportExportData data, Map<String, ImportExportService.FileBundleEntry> files) {
            this.data = data;
            this.files = files;
        }

        ImportExportData getData() {
            return data;
        }

        Map<String, ImportExportService.FileBundleEntry> getFiles() {
            return files;
        }
    }
}
