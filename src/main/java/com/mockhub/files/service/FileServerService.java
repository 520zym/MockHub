package com.mockhub.files.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.model.PageResult;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.mockhub.files.model.StoredFile;
import com.mockhub.files.repository.FileRepository;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileServerService {
    private static final Logger log = LoggerFactory.getLogger(FileServerService.class);
    private final FileRepository repository;
    private final TeamRepository teams;
    private final PermissionChecker permission;
    private final Path root;
    private final long maxBytes;

    public FileServerService(FileRepository repository, TeamRepository teams, PermissionChecker permission,
                             @Value("${data.path:./data}") String dataPath,
                             @Value("${file-server.max-size-bytes:104857600}") long maxBytes) {
        this.repository = repository; this.teams = teams; this.permission = permission;
        this.root = Paths.get(dataPath, "file-server").toAbsolutePath().normalize(); this.maxBytes = maxBytes;
    }

    public StoredFile uploadPublic(String identifier, MultipartFile file) {
        Team team = teams.findByIdentifier(identifier);
        if (team == null) throw new BizException(40404, "团队不存在");
        return store(team.getId(), file);
    }

    public StoredFile uploadManaged(String teamId, MultipartFile file) {
        permission.checkTeamAccess(teamId);
        if (teams.findById(teamId) == null) throw new BizException(40404, "团队不存在");
        return store(teamId, file);
    }

    private StoredFile store(String teamId, MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) throw new BizException(40000, "请选择上传文件");
        if (maxBytes > 0 && file.getSize() > maxBytes) throw new BizException(40000, "文件超过上传大小限制");
        String name = file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (name.isEmpty()) name = "file";
        if (name.length() > 240) throw new BizException(40000, "文件名不能超过 240 个字符");
        String id = UUID.randomUUID().toString();
        Path target = path(id), temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, "upload-", ".part");
            long total = 0;
            try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024]; int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (maxBytes > 0 && total > maxBytes) throw new BizException(40000, "文件超过上传大小限制");
                    out.write(buffer, 0, n);
                }
            }
            Files.move(temporary, target);
            StoredFile stored = new StoredFile();
            stored.setFileId(id); stored.setTeamId(teamId); stored.setFileName(name); stored.setAlias("");
            stored.setContentType(contentType(name)); stored.setSize(total); stored.setUploadedAt(Instant.now().toString());
            repository.insert(stored);
            return stored;
        } catch (IOException e) {
            deleteFailedUpload(target); throw new BizException(50000, "文件保存失败");
        } catch (RuntimeException e) {
            deleteFailedUpload(target); throw e;
        } finally {
            if (temporary != null) deleteFailedUpload(temporary);
        }
    }

    private void deleteFailedUpload(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException e) { log.warn("清理上传临时文件失败: {}", path, e); }
    }

    public Path path(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new BizException(40404, "文件不存在");
        }
        return root.resolve(id);
    }
    public StoredFile findPublic(String id) {
        path(id);
        return repository.find(id);
    }
    public StoredFile detail(String id) {
        StoredFile file = findPublic(id);
        if (file == null) throw new BizException(40404, "文件不存在");
        permission.checkTeamAccess(file.getTeamId());
        return file;
    }
    public PageResult<StoredFile> list(String teamId, String keyword, String tag, int page, int size) {
        if (teamId != null && !teamId.isEmpty()) permission.checkTeamAccess(teamId);
        if (page < 1 || size < 1 || size > 100) throw new BizException(40000, "分页范围不正确");
        String userId = SecurityContextUtil.isSuperAdmin() ? null : SecurityContextUtil.getCurrentUserId();
        return repository.list(teamId, userId, keyword, tag, page, size);
    }

    public synchronized StoredFile update(String id, String alias, List<String> tags) {
        StoredFile file = detail(id);
        permission.checkTeamAdmin(file.getTeamId());
        alias = alias == null ? "" : alias.trim();
        if (alias.length() > 200) throw new BizException(40000, "别名不能超过 200 个字符");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (tags != null) for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) continue;
            if (tag.trim().length() > 40) throw new BizException(40000, "单个标签不能超过 40 个字符");
            normalized.add(tag.trim());
        }
        if (normalized.size() > 20) throw new BizException(40000, "每个文件最多 20 个标签");
        repository.update(id, alias, new ArrayList<>(normalized));
        return detail(id);
    }

    public synchronized void delete(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) throw new BizException(40000, "每次请选择 1 至 100 个文件");
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        // 全部授权检查通过才更改数据，避免跨团队批删部分成功。
        for (String id : unique) permission.checkTeamAdmin(detail(id).getTeamId());
        repository.markDeleted(unique);
        for (String id : unique) removeDeletedFile(id);
    }

    private void removeDeletedFile(String id) {
        try {
            Files.deleteIfExists(path(id)); repository.finishDeletion(id);
        } catch (Exception e) {
            // Windows 等环境正在读取文件时删除可能失败，链接已失效，由后台重试释放磁盘。
            log.warn("文件已下线，等待重试物理删除: {}", id, e);
        }
    }
    @Scheduled(fixedDelayString = "${file-server.delete-retry-ms:3600000}", initialDelayString = "${file-server.delete-retry-ms:3600000}")
    public synchronized void retryDeletion() {
        for (String id : repository.pendingDeletion()) removeDeletedFile(id);
    }

    public void recordTransfer(String id, long bytes) {
        try { repository.recordTransfer(id, bytes, Instant.now().toString()); }
        catch (Exception e) { log.warn("文件传输统计保存失败: {}", id, e); }
    }

    /** 由服务端扩展名白名单决定类型，不信任客户端声明的可执行 MIME。 */
    public static String contentType(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "ogg": return "audio/ogg";
            case "m4a": return "audio/mp4";
            case "aac": return "audio/aac";
            case "flac": return "audio/flac";
            case "mp4": return "video/mp4";
            case "webm": return "video/webm";
            case "ogv": return "video/ogg";
            case "mov": return "video/quicktime";
            case "m4v": return "video/mp4";
            case "txt": case "log": case "csv": return "text/plain; charset=UTF-8";
            default: return "application/octet-stream";
        }
    }
}
