package com.mockhub.mock.service;

import com.mockhub.common.model.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;

/**
 * Mock 文件响应存储服务。
 * <p>
 * 文件实体存储在 data/files 下，数据库仅保存相对路径和元数据。
 */
@Service
public class MockFileStorageService {

    private final Path rootDir;
    private final long maxSizeBytes;

    @Autowired
    public MockFileStorageService(@Value("${data.path:./data}") String dataDir,
                                  @Value("${mock.file.max-size-bytes:10485760}") long maxSizeBytes) {
        this.rootDir = Paths.get(dataDir, "files").toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    public MockFileStorageService(String dataDir) {
        this(dataDir, 10L * 1024L * 1024L);
    }

    public StoredMockFile store(String teamId, MultipartFile file) {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new BizException(40000, "团队不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(40000, "上传文件不能为空");
        }
        if (maxSizeBytes > 0 && file.getSize() > maxSizeBytes) {
            throw new BizException(40000, "上传文件不能超过 " + formatSize(maxSizeBytes));
        }
        try {
            String safeName = sanitizeFileName(file.getOriginalFilename());
            String storedName = UUID.randomUUID().toString() + "-" + safeName;
            Path teamDir = rootDir.resolve(teamId).normalize();
            Files.createDirectories(teamDir);
            Path target = teamDir.resolve(storedName).normalize();
            if (!target.startsWith(rootDir)) {
                throw new BizException(40000, "非法文件路径");
            }
            file.transferTo(target.toFile());
            String relativePath = rootDir.relativize(target).toString().replace('\\', '/');
            return new StoredMockFile(relativePath, safeName, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new BizException(50000, "文件保存失败");
        }
    }

    public StoredMockFile store(String teamId, String originalFileName, String contentType,
                                long size, InputStream inputStream) {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new BizException(40000, "团队不能为空");
        }
        if (inputStream == null) {
            throw new BizException(40000, "上传文件不能为空");
        }
        if (maxSizeBytes > 0 && size > maxSizeBytes) {
            throw new BizException(40000, "上传文件不能超过 " + formatSize(maxSizeBytes));
        }
        try {
            String safeName = sanitizeFileName(originalFileName);
            String storedName = UUID.randomUUID().toString() + "-" + safeName;
            Path teamDir = rootDir.resolve(teamId).normalize();
            Files.createDirectories(teamDir);
            Path target = teamDir.resolve(storedName).normalize();
            if (!target.startsWith(rootDir)) {
                throw new BizException(40000, "非法文件路径");
            }
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            long storedSize = Files.size(target);
            if (maxSizeBytes > 0 && storedSize > maxSizeBytes) {
                Files.deleteIfExists(target);
                throw new BizException(40000, "上传文件不能超过 " + formatSize(maxSizeBytes));
            }
            String relativePath = rootDir.relativize(target).toString().replace('\\', '/');
            return new StoredMockFile(relativePath, safeName, contentType, storedSize);
        } catch (IOException e) {
            throw new BizException(50000, "文件保存失败");
        }
    }

    public Resource loadAsResource(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new BizException(40404, "文件不存在");
        }
        return new FileSystemResource(path.toFile());
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception ignored) {
            // 删除文件失败不影响接口删除或保存主流程
        }
    }

    public StorageStats getStorageStats(Collection<String> referencedPaths) {
        List<OrphanMockFile> allFiles = listFiles();
        List<OrphanMockFile> orphans = findOrphanFiles(referencedPaths);
        return new StorageStats(rootDir.toString(), maxSizeBytes, allFiles.size(), sumFileSize(allFiles),
                normalizeReferencedPaths(referencedPaths).size(), orphans.size(), sumFileSize(orphans));
    }

    public List<OrphanMockFile> findOrphanFiles(Collection<String> referencedPaths) {
        List<OrphanMockFile> allFiles = listFiles();
        if (allFiles.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> referenced = normalizeReferencedPaths(referencedPaths);
        List<OrphanMockFile> orphans = new ArrayList<>();
        for (OrphanMockFile file : allFiles) {
            if (!referenced.contains(file.getFilePath())) {
                orphans.add(file);
            }
        }
        return orphans;
    }

    private List<OrphanMockFile> listFiles() {
        if (!Files.isDirectory(rootDir)) {
            return Collections.emptyList();
        }
        List<OrphanMockFile> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String relativePath = rootDir.relativize(path).toString().replace('\\', '/');
                files.add(toOrphanFile(path, relativePath));
            });
        } catch (IOException e) {
            throw new BizException(50000, "扫描文件存储失败");
        }
        return files;
    }

    public OrphanCleanupResult deleteOrphanFiles(Collection<String> referencedPaths) {
        return deleteOrphanFiles(referencedPaths, null);
    }

    public OrphanCleanupResult deleteOrphanFiles(Collection<String> referencedPaths, Long olderThanMillis) {
        List<OrphanMockFile> orphans = findOrphanFiles(referencedPaths);
        List<OrphanMockFile> deletedFiles = new ArrayList<>();
        long deletedSize = 0L;
        for (OrphanMockFile orphan : orphans) {
            if (olderThanMillis != null && orphan.getLastModifiedAt() >= olderThanMillis) {
                continue;
            }
            try {
                Path path = resolve(orphan.getFilePath());
                if (Files.deleteIfExists(path)) {
                    deletedFiles.add(orphan);
                    deletedSize += orphan.getFileSize();
                    deleteEmptyParents(path.getParent());
                }
            } catch (Exception ignored) {
                // 单个文件删除失败不影响其他孤儿文件清理
            }
        }
        return new OrphanCleanupResult(orphans.size(), sumFileSize(orphans),
                deletedFiles.size(), deletedSize, deletedFiles);
    }

    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new BizException(40404, "文件不存在");
        }
        Path path = rootDir.resolve(relativePath).normalize();
        if (!path.startsWith(rootDir)) {
            throw new BizException(40000, "非法文件路径");
        }
        return path;
    }

    private Set<String> normalizeReferencedPaths(Collection<String> referencedPaths) {
        if (referencedPaths == null || referencedPaths.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String referencedPath : referencedPaths) {
            if (referencedPath != null && !referencedPath.trim().isEmpty()) {
                normalized.add(referencedPath.trim().replace('\\', '/'));
            }
        }
        return normalized;
    }

    private OrphanMockFile toOrphanFile(Path path, String relativePath) {
        try {
            return new OrphanMockFile(relativePath, path.getFileName().toString(),
                    Files.size(path), Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            throw new BizException(50000, "读取文件信息失败");
        }
    }

    private long sumFileSize(List<OrphanMockFile> files) {
        long total = 0L;
        for (OrphanMockFile file : files) {
            total += file.getFileSize();
        }
        return total;
    }

    private void deleteEmptyParents(Path dir) {
        Path current = dir;
        while (current != null && !current.equals(rootDir) && current.startsWith(rootDir)) {
            try {
                Files.delete(current);
                current = current.getParent();
            } catch (Exception ignored) {
                return;
            }
        }
    }

    private String sanitizeFileName(String name) {
        String fallback = "file";
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        String normalized = Paths.get(name).getFileName().toString();
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + "B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + "KB";
        }
        return (bytes / 1024L / 1024L) + "MB";
    }

    public static class StoredMockFile {
        private final String filePath;
        private final String fileName;
        private final String contentType;
        private final long fileSize;

        public StoredMockFile(String filePath, String fileName, String contentType, long fileSize) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.contentType = contentType;
            this.fileSize = fileSize;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public long getFileSize() {
            return fileSize;
        }
    }

    public static class OrphanMockFile {
        private final String filePath;
        private final String fileName;
        private final long fileSize;
        private final long lastModifiedAt;

        public OrphanMockFile(String filePath, String fileName, long fileSize, long lastModifiedAt) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.lastModifiedAt = lastModifiedAt;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public long getLastModifiedAt() {
            return lastModifiedAt;
        }
    }

    public static class OrphanCleanupResult {
        private final int orphanCount;
        private final long orphanSize;
        private final int deletedCount;
        private final long deletedSize;
        private final List<OrphanMockFile> deletedFiles;

        public OrphanCleanupResult(int orphanCount, long orphanSize, int deletedCount,
                                   long deletedSize, List<OrphanMockFile> deletedFiles) {
            this.orphanCount = orphanCount;
            this.orphanSize = orphanSize;
            this.deletedCount = deletedCount;
            this.deletedSize = deletedSize;
            this.deletedFiles = deletedFiles;
        }

        public int getOrphanCount() {
            return orphanCount;
        }

        public long getOrphanSize() {
            return orphanSize;
        }

        public int getDeletedCount() {
            return deletedCount;
        }

        public long getDeletedSize() {
            return deletedSize;
        }

        public List<OrphanMockFile> getDeletedFiles() {
            return deletedFiles;
        }
    }

    public static class StorageStats {
        private final String rootDir;
        private final long maxSizeBytes;
        private final int totalFileCount;
        private final long totalFileSize;
        private final int referencedFileCount;
        private final int orphanCount;
        private final long orphanSize;

        public StorageStats(String rootDir, long maxSizeBytes, int totalFileCount, long totalFileSize,
                            int referencedFileCount, int orphanCount, long orphanSize) {
            this.rootDir = rootDir;
            this.maxSizeBytes = maxSizeBytes;
            this.totalFileCount = totalFileCount;
            this.totalFileSize = totalFileSize;
            this.referencedFileCount = referencedFileCount;
            this.orphanCount = orphanCount;
            this.orphanSize = orphanSize;
        }

        public String getRootDir() {
            return rootDir;
        }

        public long getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public int getTotalFileCount() {
            return totalFileCount;
        }

        public long getTotalFileSize() {
            return totalFileSize;
        }

        public int getReferencedFileCount() {
            return referencedFileCount;
        }

        public int getOrphanCount() {
            return orphanCount;
        }

        public long getOrphanSize() {
            return orphanSize;
        }
    }
}
