package com.mockhub.mock.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockFileStorageServiceOrphanTest {

    @TempDir
    Path tempDir;

    @Test
    void scansAndDeletesOnlyUnreferencedFiles() throws Exception {
        MockFileStorageService storageService = new MockFileStorageService(tempDir.toString());
        Path filesDir = tempDir.resolve("files");
        Path referenced = filesDir.resolve("team-a/referenced.txt");
        Path orphan = filesDir.resolve("team-b/orphan.txt");
        Files.createDirectories(referenced.getParent());
        Files.createDirectories(orphan.getParent());
        Files.write(referenced, "still used".getBytes(StandardCharsets.UTF_8));
        Files.write(orphan, "not used".getBytes(StandardCharsets.UTF_8));

        List<MockFileStorageService.OrphanMockFile> orphans =
                storageService.findOrphanFiles(Collections.singleton("team-a/referenced.txt"));

        assertEquals(1, orphans.size());
        assertEquals("team-b/orphan.txt", orphans.get(0).getFilePath());
        assertEquals("orphan.txt", orphans.get(0).getFileName());
        assertEquals(Files.size(orphan), orphans.get(0).getFileSize());

        MockFileStorageService.OrphanCleanupResult cleanupResult =
                storageService.deleteOrphanFiles(Collections.singleton("team-a/referenced.txt"));

        assertEquals(1, cleanupResult.getDeletedCount());
        assertEquals(1, cleanupResult.getDeletedFiles().size());
        assertTrue(Files.exists(referenced));
        assertFalse(Files.exists(orphan));
        assertFalse(Files.exists(orphan.getParent()), "删除孤儿文件后应清理空目录");
    }

    @Test
    void deletesOnlyOldOrphansWhenCutoffIsProvided() throws Exception {
        MockFileStorageService storageService = new MockFileStorageService(tempDir.toString());
        Path filesDir = tempDir.resolve("files");
        Path oldOrphan = filesDir.resolve("team-a/old.txt");
        Path freshOrphan = filesDir.resolve("team-a/fresh.txt");
        Files.createDirectories(oldOrphan.getParent());
        Files.write(oldOrphan, "old".getBytes(StandardCharsets.UTF_8));
        Files.write(freshOrphan, "fresh".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(oldOrphan, java.nio.file.attribute.FileTime.fromMillis(1000L));
        Files.setLastModifiedTime(freshOrphan, java.nio.file.attribute.FileTime.fromMillis(5000L));

        MockFileStorageService.OrphanCleanupResult cleanupResult =
                storageService.deleteOrphanFiles(Collections.<String>emptySet(), 3000L);

        assertEquals(1, cleanupResult.getDeletedCount());
        assertFalse(Files.exists(oldOrphan));
        assertTrue(Files.exists(freshOrphan));
    }
}
