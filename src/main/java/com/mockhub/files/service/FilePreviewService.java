package com.mockhub.files.service;

import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 为文件管理页提供安全、有限的文本和 Office 内容预览。
 * 浏览器原生支持的 PDF、图片、音频和视频不在此处解析，调用方应使用下载流地址预览。
 */
@Service
public class FilePreviewService {

    private static final long OFFICE_MAX_BYTES = 20L * 1024 * 1024;
    private static final long TEXT_MAX_BYTES = 2L * 1024 * 1024;
    private static final int TEXT_MAX_CHARS = 200_000;
    private static final int MAX_SHEETS = 10;
    private static final int MAX_ROWS_PER_SHEET = 200;
    private static final int MAX_COLUMNS_PER_SHEET = 50;
    private static final int MAX_TABLE_CHARS = 200_000;

    /**
     * 返回文件可用的预览策略。此方法只根据安全白名单判断后缀，不会尝试猜测二进制格式。
     */
    public static String previewType(String fileName) {
        String extension = extensionOf(fileName);
        if ("html".equals(extension) || "htm".equals(extension)) {
            return "html";
        }
        if ("xls".equals(extension) || "xlsx".equals(extension)) {
            return "excel";
        }
        if ("doc".equals(extension) || "docx".equals(extension)) {
            return "word";
        }
        if ("pdf".equals(extension)) {
            return "pdf";
        }
        if ("png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension)
                || "gif".equals(extension) || "webp".equals(extension) || "bmp".equals(extension)) {
            return "image";
        }
        if ("mp3".equals(extension) || "wav".equals(extension) || "ogg".equals(extension)
                || "m4a".equals(extension) || "aac".equals(extension) || "flac".equals(extension)) {
            return "audio";
        }
        if ("mp4".equals(extension) || "webm".equals(extension) || "ogv".equals(extension)
                || "mov".equals(extension) || "m4v".equals(extension)) {
            return "video";
        }
        if ("txt".equals(extension) || "csv".equals(extension) || "log".equals(extension)
                || "json".equals(extension) || "xml".equals(extension) || "yml".equals(extension)
                || "yaml".equals(extension) || "properties".equals(extension) || "md".equals(extension)
                // SVG 可能携带脚本；仅按源文本展示，管理页不得同源内联渲染。
                || "svg".equals(extension)) {
            return "text";
        }
        return "unsupported";
    }

    /**
     * 解析可安全展示的文件内容。所有异常都转换为可显示的提示，避免预览接口因格式错误返回 500。
     */
    public Map<String, Object> preview(Path path, String fileName) {
        if (path == null || !Files.isRegularFile(path)) {
            return unsupported("文件不存在或不是普通文件。");
        }

        String type = previewType(fileName);
        try {
            long size = Files.size(path);
            if (("excel".equals(type) || "word".equals(type)) && size > OFFICE_MAX_BYTES) {
                return unsupported("Office 文件超过 20MB，建议下载后查看。");
            }
            if (("text".equals(type) || "html".equals(type)) && size > TEXT_MAX_BYTES) {
                return unsupported("文本文件超过 2MB，建议下载后查看。");
            }

            if ("text".equals(type)) {
                return textResult(readText(path), "text");
            }
            if ("html".equals(type)) {
                return textResult(readText(path), "html");
            }
            if ("excel".equals(type)) {
                return excelResult(path);
            }
            if ("word".equals(type)) {
                return textResult(readWord(path, extensionOf(fileName)), "text");
            }
            if ("pdf".equals(type) || "image".equals(type) || "audio".equals(type) || "video".equals(type)) {
                return unsupported("该格式请使用浏览器原生预览。");
            }
            return unsupported("暂不支持预览此文件格式。");
        } catch (Exception e) {
            return unsupported("文件无法解析，建议下载后查看。");
        }
    }

    private Map<String, Object> excelResult(Path path) throws IOException {
        List<Map<String, Object>> sheets = new ArrayList<Map<String, Object>>();
        boolean truncated = false;
        int[] remainingChars = new int[] {MAX_TABLE_CHARS};
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path));
             Workbook workbook = WorkbookFactory.create(input)) {
            DataFormatter formatter = new DataFormatter(Locale.getDefault());
            int sheetCount = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);
            truncated = workbook.getNumberOfSheets() > MAX_SHEETS;
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Object> sheetData = new LinkedHashMap<String, Object>();
                sheetData.put("name", sheet.getSheetName());
                List<List<String>> rows = new ArrayList<List<String>>();
                int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS_PER_SHEET - 1);
                if (sheet.getLastRowNum() >= MAX_ROWS_PER_SHEET) {
                    truncated = true;
                }
                for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    List<String> values = new ArrayList<String>();
                    for (int columnIndex = 0; columnIndex < MAX_COLUMNS_PER_SHEET; columnIndex++) {
                        Cell cell = row == null ? null : row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String value = cell == null || remainingChars[0] <= 0 ? "" : formatter.formatCellValue(cell);
                        if (value.length() > remainingChars[0]) {
                            value = value.substring(0, remainingChars[0]);
                            truncated = true;
                        }
                        remainingChars[0] -= value.length();
                        values.add(value);
                    }
                    rows.add(values);
                }
                sheetData.put("rows", rows);
                sheets.add(sheetData);
            }
        }
        Map<String, Object> result = baseResult("table", truncated, "");
        result.put("sheets", sheets);
        return result;
    }

    private String readWord(Path path, String extension) throws IOException {
        StringBuilder text = new StringBuilder();
        if ("docx".equals(extension)) {
            try (InputStream input = new BufferedInputStream(Files.newInputStream(path));
                 XWPFDocument document = new XWPFDocument(input)) {
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    appendLimited(text, paragraph.getText());
                }
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            appendLimited(text, cell.getText());
                        }
                    }
                }
            }
        } else {
            try (InputStream input = new BufferedInputStream(Files.newInputStream(path));
                 POIFSFileSystem fileSystem = new POIFSFileSystem(input);
                 WordExtractor extractor = new WordExtractor(fileSystem)) {
                for (String paragraph : extractor.getParagraphText()) {
                    appendLimited(text, paragraph);
                }
            }
        }
        return text.toString();
    }

    private void appendLimited(StringBuilder target, String value) {
        if (value == null || target.length() > TEXT_MAX_CHARS) {
            return;
        }
        int available = TEXT_MAX_CHARS + 1 - target.length();
        if (value.length() > available) {
            target.append(value, 0, available);
        } else {
            target.append(value);
        }
        if (target.length() < TEXT_MAX_CHARS) {
            target.append('\n');
        }
    }

    private String readText(Path path) throws IOException {
        byte[] bytes;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0 && output.size() <= TEXT_MAX_BYTES) {
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        Charset charset = StandardCharsets.UTF_8;
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            charset = StandardCharsets.UTF_16BE;
            offset = 2;
        } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            charset = StandardCharsets.UTF_16LE;
            offset = 2;
        }
        return new String(bytes, offset, bytes.length - offset, charset);
    }

    private Map<String, Object> textResult(String text, String kind) {
        boolean truncated = text.length() > TEXT_MAX_CHARS;
        if (truncated) {
            text = text.substring(0, TEXT_MAX_CHARS);
        }
        Map<String, Object> result = baseResult(kind, truncated, "");
        result.put("text", text);
        return result;
    }

    private Map<String, Object> unsupported(String message) {
        return baseResult("unsupported", false, message);
    }

    private Map<String, Object> baseResult(String kind, boolean truncated, String message) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("kind", kind);
        result.put("text", "");
        result.put("sheets", Collections.emptyList());
        result.put("truncated", truncated);
        result.put("message", message);
        return result;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
