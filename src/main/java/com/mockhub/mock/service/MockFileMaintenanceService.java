package com.mockhub.mock.service;

import com.mockhub.common.config.MockFileProperties;
import com.mockhub.mock.repository.ApiResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class MockFileMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MockFileMaintenanceService.class);

    private final ApiResponseRepository apiResponseRepository;
    private final MockFileStorageService mockFileStorageService;
    private final MockFileProperties mockFileProperties;

    public MockFileMaintenanceService(ApiResponseRepository apiResponseRepository,
                                      MockFileStorageService mockFileStorageService,
                                      MockFileProperties mockFileProperties) {
        this.apiResponseRepository = apiResponseRepository;
        this.mockFileStorageService = mockFileStorageService;
        this.mockFileProperties = mockFileProperties;
    }

    public OrphanScanResult scanOrphanFiles() {
        List<String> referencedPaths = apiResponseRepository.findAllFilePaths();
        List<MockFileStorageService.OrphanMockFile> files =
                mockFileStorageService.findOrphanFiles(referencedPaths);
        return new OrphanScanResult(files.size(), sumFileSize(files), files);
    }

    public MockFileStorageService.OrphanCleanupResult cleanOrphanFiles() {
        return mockFileStorageService.deleteOrphanFiles(apiResponseRepository.findAllFilePaths());
    }

    public MockFileStorageService.StorageStats getStorageStats() {
        return mockFileStorageService.getStorageStats(apiResponseRepository.findAllFilePaths());
    }

    @Scheduled(fixedDelayString = "${mock.file.orphan-cleanup-interval-ms:3600000}",
            initialDelayString = "${mock.file.orphan-cleanup-interval-ms:3600000}")
    public void autoCleanOrphanFiles() {
        if (!mockFileProperties.isOrphanAutoCleanupEnabled()) {
            return;
        }
        long retentionMillis = Math.max(1, mockFileProperties.getOrphanRetentionHours()) * 60L * 60L * 1000L;
        long cutoffMillis = new Date().getTime() - retentionMillis;
        MockFileStorageService.OrphanCleanupResult result =
                mockFileStorageService.deleteOrphanFiles(apiResponseRepository.findAllFilePaths(), cutoffMillis);
        if (result.getDeletedCount() > 0) {
            log.info("自动清理孤儿文件: deletedCount={}, deletedSize={}",
                    result.getDeletedCount(), result.getDeletedSize());
        }
    }

    private long sumFileSize(List<MockFileStorageService.OrphanMockFile> files) {
        long total = 0L;
        for (MockFileStorageService.OrphanMockFile file : files) {
            total += file.getFileSize();
        }
        return total;
    }

    public static class OrphanScanResult {
        private final int orphanCount;
        private final long orphanSize;
        private final List<MockFileStorageService.OrphanMockFile> files;

        public OrphanScanResult(int orphanCount, long orphanSize,
                                List<MockFileStorageService.OrphanMockFile> files) {
            this.orphanCount = orphanCount;
            this.orphanSize = orphanSize;
            this.files = files;
        }

        public int getOrphanCount() {
            return orphanCount;
        }

        public long getOrphanSize() {
            return orphanSize;
        }

        public List<MockFileStorageService.OrphanMockFile> getFiles() {
            return files;
        }
    }
}
