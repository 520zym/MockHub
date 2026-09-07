package com.mockhub.files.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.model.Result;
import com.mockhub.files.model.StoredFile;
import com.mockhub.files.service.FilePreviewService;
import com.mockhub.files.service.FileServerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class FileManagementController {
    private final FileServerService service;
    private final FilePreviewService previews;
    private final ObjectMapper mapper;
    private final String publicBaseUrl;
    public FileManagementController(FileServerService service, FilePreviewService previews, ObjectMapper mapper,
                                    @Value("${file-server.public-base-url:}") String publicBaseUrl) {
        this.service = service; this.previews = previews; this.mapper = mapper;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping("/file-server/{teamIdentifier}/upload")
    public Result<Map<String, Object>> publicUpload(@PathVariable String teamIdentifier, @RequestParam("file") MultipartFile file) {
        return Result.ok(view(service.uploadPublic(teamIdentifier, file)));
    }
    @PostMapping("/api/files/upload")
    public Result<Map<String, Object>> upload(@RequestParam String teamId, @RequestParam("file") MultipartFile file) {
        return Result.ok(view(service.uploadManaged(teamId, file)));
    }
    @GetMapping("/api/files")
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required=false) String teamId,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) String tag,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        PageResult<StoredFile> found = service.list(teamId, keyword, tag, page, size);
        List<Map<String, Object>> items = new ArrayList<>();
        for (StoredFile f : found.getItems()) items.add(view(f));
        return Result.ok(PageResult.of(items, found.getTotal(), found.getPage(), found.getSize()));
    }
    @GetMapping("/api/files/{id}")
    public Result<Map<String, Object>> detail(@PathVariable String id) { return Result.ok(view(service.detail(id))); }
    @PutMapping("/api/files/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @RequestBody Metadata request) {
        return Result.ok(view(service.update(id, request.getAlias(), request.getTags())));
    }
    @DeleteMapping("/api/files/{id}")
    public Result<Void> delete(@PathVariable String id) { service.delete(Collections.singletonList(id)); return Result.ok(); }
    @PostMapping("/api/files/batch-delete")
    public Result<Void> deleteBatch(@RequestBody Batch request) { service.delete(request.getFileIds()); return Result.ok(); }
    @GetMapping("/api/files/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable String id) {
        StoredFile file = service.detail(id);
        return Result.ok(previews.preview(service.path(id), file.getFileName()));
    }

    private Map<String, Object> view(StoredFile file) {
        Map<String, Object> data = mapper.convertValue(file, new TypeReference<Map<String, Object>>() {});
        String base = publicBaseUrl.isEmpty() ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() : publicBaseUrl;
        data.put("downloadUrl", base + "/files/" + file.getFileId());
        data.put("previewType", FilePreviewService.previewType(file.getFileName()));
        return data;
    }
    public static class Metadata {
        private String alias;
        private List<String> tags;
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
    public static class Batch {
        private List<String> fileIds;
        public List<String> getFileIds() { return fileIds; }
        public void setFileIds(List<String> fileIds) { this.fileIds = fileIds; }
    }
}
