package com.mockhub.files.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePreviewServiceTest {

    @TempDir
    Path tempDir;

    private final FilePreviewService service = new FilePreviewService();

    @Test
    void previewTypeUsesSafeWhitelist() {
        assertEquals("text", FilePreviewService.previewType("example.JSON"));
        assertEquals("html", FilePreviewService.previewType("index.html"));
        assertEquals("text", FilePreviewService.previewType("icon.svg"));
        assertEquals("excel", FilePreviewService.previewType("data.xlsx"));
        assertEquals("word", FilePreviewService.previewType("document.doc"));
        assertEquals("unsupported", FilePreviewService.previewType("script.exe"));
    }

    @Test
    void readsUtf16BomText() throws Exception {
        Path file = tempDir.resolve("message.txt");
        byte[] content = "你好 MockHub".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[content.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(content, 0, bytes, 2, content.length);
        Files.write(file, bytes);

        Map<String, Object> result = service.preview(file, "message.txt");

        assertEquals("text", result.get("kind"));
        assertEquals("你好 MockHub", result.get("text"));
        assertFalse((Boolean) result.get("truncated"));
    }

    @Test
    void truncatesLongTextInsteadOfReadingUnlimitedContent() throws Exception {
        Path file = tempDir.resolve("large.log");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 200_100; i++) {
            content.append('x');
        }
        Files.write(file, content.toString().getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = service.preview(file, "large.log");

        assertTrue((Boolean) result.get("truncated"));
        assertEquals(200_000, ((String) result.get("text")).length());
    }

    @Test
    void previewsXlsxAsLimitedTable() throws Exception {
        Path file = tempDir.resolve("table.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(file)) {
            workbook.createSheet("第一页").createRow(0).createCell(0).setCellValue("标题");
            workbook.getSheetAt(0).getRow(0).createCell(1).setCellValue(12.5D);
            workbook.write(output);
        }

        Map<String, Object> result = service.preview(file, "table.xlsx");

        assertEquals("table", result.get("kind"));
        List<Map<String, Object>> sheets = (List<Map<String, Object>>) result.get("sheets");
        assertEquals("第一页", sheets.get(0).get("name"));
        List<List<String>> rows = (List<List<String>>) sheets.get(0).get("rows");
        assertEquals("标题", rows.get(0).get(0));
        assertEquals("12.5", rows.get(0).get(1));
    }

    @Test
    void previewsXlsAsLimitedTable() throws Exception {
        Path file = tempDir.resolve("table.xls");
        try (HSSFWorkbook workbook = new HSSFWorkbook();
             OutputStream output = Files.newOutputStream(file)) {
            workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("legacy");
            workbook.write(output);
        }

        Map<String, Object> result = service.preview(file, "table.xls");

        assertEquals("table", result.get("kind"));
        List<Map<String, Object>> sheets = (List<Map<String, Object>>) result.get("sheets");
        List<List<String>> rows = (List<List<String>>) sheets.get(0).get("rows");
        assertEquals("legacy", rows.get(0).get(0));
    }

    @Test
    void previewsDocxText() throws Exception {
        Path file = tempDir.resolve("document.docx");
        try (XWPFDocument document = new XWPFDocument();
             OutputStream output = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("Office 正文");
            document.write(output);
        }

        Map<String, Object> result = service.preview(file, "document.docx");

        assertEquals("text", result.get("kind"));
        assertTrue(((String) result.get("text")).contains("Office 正文"));
    }

    @Test
    void previewsSvgAsTextInsteadOfNativeImage() throws Exception {
        Path file = tempDir.resolve("icon.svg");
        Files.write(file, "<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = service.preview(file, "icon.svg");

        assertEquals("text", result.get("kind"));
        assertTrue(((String) result.get("text")).contains("<script>"));
    }

    @Test
    void rejectsOversizedTextBeforeParsing() throws Exception {
        Path file = tempDir.resolve("oversized.txt");
        Files.write(file, new byte[2 * 1024 * 1024 + 1]);

        Map<String, Object> result = service.preview(file, "oversized.txt");

        assertEquals("unsupported", result.get("kind"));
        assertTrue(((String) result.get("message")).contains("2MB"));
    }

    @Test
    void returnsReadableResultForCorruptOfficeFile() throws Exception {
        Path file = tempDir.resolve("broken.xlsx");
        Files.write(file, "not an Excel workbook".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = service.preview(file, "broken.xlsx");

        assertEquals("unsupported", result.get("kind"));
        assertTrue(((String) result.get("message")).contains("无法解析"));
    }

    @Test
    void limitsTotalCharactersAcrossExcelCells() throws Exception {
        Path file = tempDir.resolve("large-cells.xlsx");
        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 32_767; i++) {
            longValue.append('x');
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(file)) {
            org.apache.poi.ss.usermodel.Row row = workbook.createSheet("Sheet1").createRow(0);
            for (int column = 0; column < 7; column++) {
                row.createCell(column).setCellValue(longValue.toString());
            }
            workbook.write(output);
        }

        Map<String, Object> result = service.preview(file, "large-cells.xlsx");

        List<Map<String, Object>> sheets = (List<Map<String, Object>>) result.get("sheets");
        List<List<String>> rows = (List<List<String>>) sheets.get(0).get("rows");
        int total = 0;
        for (String value : rows.get(0)) {
            total += value.length();
        }
        assertEquals("table", result.get("kind"));
        assertTrue((Boolean) result.get("truncated"));
        assertTrue(total <= 200_000);
    }
}
