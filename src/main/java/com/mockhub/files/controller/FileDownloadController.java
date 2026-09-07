package com.mockhub.files.controller;

import com.mockhub.common.model.BizException;
import com.mockhub.files.model.StoredFile;
import com.mockhub.files.service.FileServerService;
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
import java.util.List;

/** 公开下载端点：不可变 fileId、流式输出、单段 Range 与条件请求。 */
@RestController
public class FileDownloadController {
    private final FileServerService service;
    public FileDownloadController(FileServerService service) { this.service = service; }

    @RequestMapping(value = "/files/{fileId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void download(@PathVariable String fileId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        StoredFile file;
        try { file = service.findPublic(fileId); }
        catch (BizException e) { response.setStatus(404); return; }
        if (file == null) { response.setStatus(404); return; }
        Path path = service.path(fileId);
        if (!Files.isRegularFile(path)) { response.setStatus(404); return; }
        // 先打开文件；与删除并发时，新请求得到 404，已经开始的传输可正常结束。
        RandomAccessFile input;
        try { input = new RandomAccessFile(path.toFile(), "r"); }
        catch (IOException e) { response.setStatus(404); return; }
        try (RandomAccessFile stream = input) {
            long length = stream.length();
            long modified = Instant.parse(file.getUploadedAt()).toEpochMilli() / 1000 * 1000;
            String etag = "\"" + fileId + "\"";
            response.setHeader("ETag", etag);
            response.setDateHeader("Last-Modified", modified);
            response.setHeader("Accept-Ranges", "bytes");
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (matchesEtag(ifNoneMatch, etag) || (ifNoneMatch == null && dateHeader(request, "If-Modified-Since") >= modified)) {
                response.setStatus(304); return;
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
                    response.setStatus(416); response.setHeader("Content-Range", "bytes */" + length); return;
                }
            }
            boolean inline = "true".equals(request.getParameter("inline"))
                    && (file.getContentType().startsWith("image/") || file.getContentType().startsWith("audio/")
                    || file.getContentType().startsWith("video/") || "application/pdf".equals(file.getContentType()));
            response.setContentType(file.getContentType());
            response.setHeader("Content-Disposition", ContentDisposition.builder(inline ? "inline" : "attachment")
                    .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(Math.max(0, end - start + 1));
            if (head) return;
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
