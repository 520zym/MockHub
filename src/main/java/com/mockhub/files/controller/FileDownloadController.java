package com.mockhub.files.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.model.BizException;
import com.mockhub.files.model.StoredFile;
import com.mockhub.files.service.FileServerService;
import com.mockhub.log.model.RequestLog;
import com.mockhub.log.service.LogService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpRange;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 公开下载端点：不可变 fileId、流式输出、单段 Range 与条件请求。 */
@RestController
public class FileDownloadController {
    private final FileServerService service;
    private final LogService logService;
    private final ObjectMapper objectMapper;
    public FileDownloadController(FileServerService service, LogService logService, ObjectMapper objectMapper) {
        this.service = service; this.logService = logService; this.objectMapper = objectMapper;
    }

    @RequestMapping(value = "/files/{fileId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void download(@PathVariable String fileId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        long startedAt = System.currentTimeMillis();
        StoredFile logged = null;
        try {
            logged = stream(fileId, request, response);
        } finally {
            // 文件链接的每次真实访问都进入请求日志，与 Mock 接口调用共用一套统计口径。
            writeAccessLog(fileId, logged, request, response, System.currentTimeMillis() - startedAt);
        }
    }

    /** 返回已解析的文件元数据；未找到时返回 null，便于外层只记录真实命中的下载。 */
    private StoredFile stream(String fileId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        StoredFile file;
        try { file = service.findPublic(fileId); }
        catch (BizException e) { response.setStatus(404); return null; }
        if (file == null) { response.setStatus(404); return null; }
        Path path = service.path(fileId);
        if (!Files.isRegularFile(path)) { response.setStatus(404); return null; }
        // 先打开文件；与删除并发时，新请求得到 404，已经开始的传输可正常结束。
        RandomAccessFile input;
        try { input = new RandomAccessFile(path.toFile(), "r"); }
        catch (IOException e) { response.setStatus(404); return null; }
        try (RandomAccessFile stream = input) {
            long length = stream.length();
            long modified = Instant.parse(file.getUploadedAt()).toEpochMilli() / 1000 * 1000;
            String etag = "\"" + fileId + "\"";
            response.setHeader("ETag", etag);
            response.setDateHeader("Last-Modified", modified);
            response.setHeader("Accept-Ranges", "bytes");
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (matchesEtag(ifNoneMatch, etag) || (ifNoneMatch == null && dateHeader(request, "If-Modified-Since") >= modified)) {
                response.setStatus(304); return file;
            }
            boolean head = "HEAD".equals(request.getMethod());
            long start = 0, end = length - 1;
            String range = request.getHeader("Range");
            String ifRange = request.getHeader("If-Range");
            boolean rangeAllowed = ifRange == null || etag.equals(ifRange) || dateHeader(request, "If-Range") >= modified;
            if (!head && range != null && rangeAllowed) {
                try {
                    List<HttpRange> ranges = HttpRange.parseRanges(range);
                    // 多段 Range 按协议允许忽略并返回完整 200；常见续传使用单段。
                    if (ranges.size() == 1) {
                        if (length == 0) throw new IllegalArgumentException();
                        start = ranges.get(0).getRangeStart(length);
                        end = ranges.get(0).getRangeEnd(length);
                        if (start < 0 || start >= length || end < start) throw new IllegalArgumentException();
                        response.setStatus(206);
                        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
                    }
                } catch (IllegalArgumentException e) {
                    response.setStatus(416); response.setHeader("Content-Range", "bytes */" + length); return file;
                }
            }
            boolean inline = "true".equals(request.getParameter("inline"))
                    && (file.getContentType().startsWith("image/") || file.getContentType().startsWith("audio/")
                    || file.getContentType().startsWith("video/") || "application/pdf".equals(file.getContentType()));
            response.setContentType(file.getContentType());
            response.setHeader("Content-Disposition", ContentDisposition.builder(inline ? "inline" : "attachment")
                    .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(Math.max(0, end - start + 1));
            if (head) return file;
            long written = 0;
            boolean complete = false;
            try {
                stream.seek(start);
                byte[] buffer = new byte[64 * 1024];
                long remaining = end - start + 1;
                while (remaining > 0) {
                    int read = stream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) break;
                    response.getOutputStream().write(buffer, 0, read);
                    written += read; remaining -= read;
                }
                response.getOutputStream().flush();
                complete = remaining == 0;
            } catch (IOException e) {
                // 客户端中断连接：不尝试改写流为 JSON；已写出的内容仍计入传输量。
            } finally {
                if (written > 0 || complete) service.recordTransfer(fileId, written);
            }
        }
        return file;
    }

    /**
     * 文件链接访问日志：只记录真实命中文件（200/206/304）的请求，
     * 不记录 404/416，避免探测路径污染调用统计；请求头只保留 User-Agent 与 Referer。
     */
    private void writeAccessLog(String fileId, StoredFile file, HttpServletRequest request,
                                HttpServletResponse response, long durationMs) {
        if (file == null) return;
        int status = response.getStatus();
        if (status != 200 && status != 206 && status != 304) return;
        try {
            RequestLog entry = new RequestLog();
            entry.setId(UUID.randomUUID().toString());
            entry.setTeamId(file.getTeamId());
            entry.setApiId(null);
            entry.setApiPath("/files/" + fileId);
            entry.setMethod(request.getMethod());
            entry.setResponseCode(status);
            entry.setDurationMs(durationMs);
            entry.setTargetType("FILE");
            entry.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            Map<String, String> headers = new LinkedHashMap<>();
            if (request.getHeader("User-Agent") != null) headers.put("user-agent", request.getHeader("User-Agent"));
            if (request.getHeader("Referer") != null) headers.put("referer", request.getHeader("Referer"));
            entry.setRequestHeaders(objectMapper.writeValueAsString(headers));
            entry.setRequestBody("");
            Map<String, String> params = new LinkedHashMap<>();
            if (request.getQueryString() != null) params.put("query", request.getQueryString());
            if (request.getHeader("Range") != null) params.put("range", request.getHeader("Range"));
            entry.setRequestParams(objectMapper.writeValueAsString(params));
            logService.asyncLogRequest(entry);
        } catch (Exception e) {
            // 统计失败不影响下载本身
        }
    }

    private static boolean matchesEtag(String value, String etag) {
        if (value == null) return false;
        for (String part : value.split(",")) {
            String token = part.trim();
            if ("*".equals(token) || etag.equals(token) || ("W/" + etag).equals(token)) return true;
        }
        return false;
    }
    private static long dateHeader(HttpServletRequest request, String name) {
        try { return request.getDateHeader(name); } catch (IllegalArgumentException e) { return -1; }
    }
}
