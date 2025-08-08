package com.jd.genie.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.util.ImageHelper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OCR服务，用于从图像和PDF文件中提取文本，特别优化表格识别
 */
@Slf4j
@Service
public class OcrService {

    private final Tesseract tesseract;
    
    // 表格检测模式 - 检测表格结构的正则表达式
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "(?m)^\\s*([^\\n]+)\\s*\\|\\s*([^\\n]+)\\s*\\|\\s*([^\\n]+).*$", 
        Pattern.MULTILINE
    );
    
    // 数字和货币模式 - 用于识别表格中的数值数据
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "\\d+[.,]?\\d*[%$€¥￥]?|[$€¥￥][\\d.,]+", 
        Pattern.MULTILINE
    );

    public OcrService() {
        this.tesseract = new Tesseract();
        initializeTesseract();
    }

    private void initializeTesseract() {
        try {
            // 设置Tesseract数据路径 - 尝试多个可能的路径
            String[] possiblePaths = {
                "/usr/local/share/tessdata",
                "/usr/share/tessdata", 
                "/opt/homebrew/share/tessdata",
                System.getenv("TESSDATA_PREFIX"),
                "./tessdata"
            };
            
            for (String path : possiblePaths) {
                if (path != null && Files.exists(Path.of(path))) {
                    tesseract.setDatapath(path);
                    log.info("Tesseract data path set to: {}", path);
                    break;
                }
            }
            
            // 设置语言 - 支持中英文
            tesseract.setLanguage("eng+chi_sim");
            
            // 设置OCR引擎模式 - 优化表格识别
            tesseract.setOcrEngineMode(1); // LSTM OCR engine only
            
            // 设置页面分割模式 - 适用于表格和结构化文本
            tesseract.setPageSegMode(6); // Assume uniform block of text
            
            // 设置变量以优化表格识别
            tesseract.setVariable("tessedit_char_whitelist", 
                "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" +
                ",.!?:;-_()[]{}/$%€¥￥+= \n\t|" +
                "一二三四五六七八九十百千万亿元角分");
                
        } catch (Exception e) {
            log.error("Failed to initialize Tesseract OCR", e);
        }
    }

    /**
     * 从图像文件中提取文本 - 使用空间感知的OCR
     */
    public OcrResult extractTextFromImage(File imageFile) {
        try {
            log.info("Starting spatial OCR extraction from image: {}", imageFile.getName());
            
            BufferedImage image = ImageIO.read(imageFile);
            
            // 使用空间感知的OCR提取
            return performSpatialOcr(image, imageFile.getName(), 1);
            
        } catch (IOException e) {
            log.error("Error extracting text from image: {}", imageFile.getName(), e);
            return OcrResult.error("OCR extraction failed: " + e.getMessage());
        }
    }

    /**
     * 从PDF文件中提取文本（通过OCR）
     */
    public OcrResult extractTextFromPdf(File pdfFile) {
        try {
            log.info("Starting OCR extraction from PDF: {}", pdfFile.getName());
            
            StringBuilder allText = new StringBuilder();
            List<TableCandidate> allTables = new ArrayList<>();
            
            try (PDDocument document = PDDocument.load(pdfFile)) {
                PDFRenderer renderer = new PDFRenderer(document);
                
                for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                    log.debug("Processing page {} of {} with spatial OCR", pageIndex + 1, document.getNumberOfPages());
                    
                    // 将PDF页面转换为图像
                    BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);
                    
                    // 使用空间感知的OCR
                    OcrResult pageResult = performSpatialOcr(pageImage, pdfFile.getName() + "_page_" + (pageIndex + 1), pageIndex + 1);
                    
                    if (pageResult.isSuccess() && pageResult.getExtractedText() != null && !pageResult.getExtractedText().trim().isEmpty()) {
                        allText.append("=== Page ").append(pageIndex + 1).append(" ===\n");
                        allText.append(pageResult.getExtractedText()).append("\n\n");
                        
                        // 添加页面检测到的表格
                        allTables.addAll(pageResult.getDetectedTables());
                    }
                }
            }
            
            return processExtractedText(allText.toString(), pdfFile.getName(), allTables);
            
        } catch (Exception e) {
            log.error("Error extracting text from PDF: {}", pdfFile.getName(), e);
            return OcrResult.error("PDF OCR extraction failed: " + e.getMessage());
        }
    }

    /**
     * 从字节数组中提取文本
     */
    public OcrResult extractTextFromBytes(byte[] imageData, String filename) {
        try {
            log.info("Starting OCR extraction from byte array for: {}", filename);
            
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                return OcrResult.error("Unable to read image from byte array");
            }
            
            String extractedText = tesseract.doOCR(image);
            
            return processExtractedText(extractedText, filename);
            
        } catch (Exception e) {
            log.error("Error extracting text from byte array for: {}", filename, e);
            return OcrResult.error("OCR extraction failed: " + e.getMessage());
        }
    }

    /**
     * 处理提取的文本，识别表格和结构化数据
     */
    private OcrResult processExtractedText(String rawText, String filename) {
        return processExtractedText(rawText, filename, new ArrayList<>());
    }
    
    private OcrResult processExtractedText(String rawText, String filename, List<TableCandidate> existingTables) {
        if (rawText == null || rawText.trim().isEmpty()) {
            log.warn("No text extracted from: {}", filename);
            return OcrResult.empty("No text could be extracted from the document");
        }
        
        // 清理文本
        String cleanedText = cleanExtractedText(rawText);
        
        // 检测表格
        List<TableCandidate> tables = new ArrayList<>(existingTables);
        if (existingTables.isEmpty()) {
            tables.addAll(detectTables(cleanedText, 1));
        }
        
        // 计算置信度
        double confidence = calculateConfidence(rawText);
        
        log.info("OCR extraction completed for {}: {} characters, {} tables, confidence: {:.2f}%", 
                filename, cleanedText.length(), tables.size(), confidence * 100);
        
        return OcrResult.success(cleanedText, tables, confidence);
    }

    /**
     * 清理OCR提取的文本
     */
    private String cleanExtractedText(String rawText) {
        return rawText
            // 移除多余的空白字符
            .replaceAll("\\s+", " ")
            // 修复常见的OCR错误
            .replace("0", "O") // 在某些上下文中，0可能被错误识别为O
            .replace("1", "l") // 1可能被错误识别为l
            // 保留表格结构的换行符
            .replaceAll("(?m)^\\s*\\|", "|")
            .trim();
    }

    /**
     * 检测文本中的表格候选项 - 旧的基于文本的方法（已被空间OCR替代）
     * @deprecated 使用 detectTablesFromSpatialData 替代
     */
    @Deprecated
    private List<TableCandidate> detectTables(String text, int pageNumber) {
        List<TableCandidate> tables = new ArrayList<>();
        
        // 按行分割文本
        String[] lines = text.split("\n");
        List<String> currentTable = new ArrayList<>();
        int tableStartLine = -1;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 检测表格行（包含管道符或多个数字/货币值）
            if (isTableRow(line)) {
                if (currentTable.isEmpty()) {
                    tableStartLine = i + 1;
                }
                currentTable.add(line);
            } else {
                // 如果当前行不是表格行，且我们有累积的表格行
                if (currentTable.size() >= 2) { // 至少需要2行才认为是表格
                    TableCandidate table = new TableCandidate();
                    table.setContent(String.join("\n", currentTable));
                    table.setStartLine(tableStartLine);
                    table.setEndLine(i);
                    table.setPageNumber(pageNumber);
                    table.setRowCount(currentTable.size());
                    table.setColumnCount(estimateColumnCount(currentTable));
                    table.setConfidence(calculateTableConfidence(currentTable));
                    
                    tables.add(table);
                    log.debug("Detected table: rows={}, cols={}, confidence={:.2f}%", 
                             table.getRowCount(), table.getColumnCount(), table.getConfidence() * 100);
                }
                currentTable.clear();
            }
        }
        
        // 处理文档末尾的表格
        if (currentTable.size() >= 2) {
            TableCandidate table = new TableCandidate();
            table.setContent(String.join("\n", currentTable));
            table.setStartLine(tableStartLine);
            table.setEndLine(lines.length);
            table.setPageNumber(pageNumber);
            table.setRowCount(currentTable.size());
            table.setColumnCount(estimateColumnCount(currentTable));
            table.setConfidence(calculateTableConfidence(currentTable));
            
            tables.add(table);
        }
        
        return tables;
    }

    /**
     * 判断一行是否可能是表格行
     */
    private boolean isTableRow(String line) {
        if (line.length() < 3) return false;
        
        // 包含管道符的行
        if (line.contains("|") && line.split("\\|").length >= 2) {
            return true;
        }
        
        // 包含多个数字或货币值的行
        Matcher numberMatcher = NUMBER_PATTERN.matcher(line);
        int numberCount = 0;
        while (numberMatcher.find()) {
            numberCount++;
        }
        
        // 包含多个制表符或大量空格分隔的内容
        if (numberCount >= 2 || line.split("\\t").length >= 3 || line.split("\\s{2,}").length >= 3) {
            return true;
        }
        
        return false;
    }

    /**
     * 估算表格列数
     */
    private int estimateColumnCount(List<String> tableRows) {
        if (tableRows.isEmpty()) return 0;
        
        int maxColumns = 0;
        for (String row : tableRows) {
            int columns = Math.max(
                row.split("\\|").length,
                Math.max(
                    row.split("\\t").length,
                    row.split("\\s{2,}").length
                )
            );
            maxColumns = Math.max(maxColumns, columns);
        }
        
        return maxColumns;
    }

    /**
     * 计算表格识别的置信度
     */
    private double calculateTableConfidence(List<String> tableRows) {
        if (tableRows.isEmpty()) return 0.0;
        
        double score = 0.0;
        int totalChecks = 0;
        
        for (String row : tableRows) {
            totalChecks++;
            
            // 包含管道符得高分
            if (row.contains("|")) score += 0.8;
            
            // 包含数字得分
            if (NUMBER_PATTERN.matcher(row).find()) score += 0.6;
            
            // 包含制表符得分
            if (row.contains("\t")) score += 0.4;
            
            // 结构一致性得分
            if (row.split("\\s{2,}").length >= 2) score += 0.3;
        }
        
        return totalChecks > 0 ? Math.min(1.0, score / totalChecks) : 0.0;
    }

    /**
     * 计算整体OCR置信度
     */
    private double calculateConfidence(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        
        // 基于文本特征计算置信度
        double score = 0.5; // 基础分数
        
        // 包含常见单词提高置信度
        if (text.matches(".*[a-zA-Z]{3,}.*")) score += 0.2;
        
        // 包含数字提高置信度
        if (text.matches(".*\\d+.*")) score += 0.1;
        
        // 包含标点符号提高置信度
        if (text.matches(".*[.,!?;:].*")) score += 0.1;
        
        // 文本长度影响置信度
        if (text.length() > 100) score += 0.1;
        
        return Math.min(1.0, score);
    }

    /**
     * OCR结果类
     */
    public static class OcrResult {
        private final boolean success;
        private final String extractedText;
        private final List<TableCandidate> detectedTables;
        private final double confidence;
        private final String errorMessage;

        private OcrResult(boolean success, String extractedText, List<TableCandidate> detectedTables, 
                         double confidence, String errorMessage) {
            this.success = success;
            this.extractedText = extractedText;
            this.detectedTables = detectedTables != null ? detectedTables : new ArrayList<>();
            this.confidence = confidence;
            this.errorMessage = errorMessage;
        }

        public static OcrResult success(String text, List<TableCandidate> tables, double confidence) {
            return new OcrResult(true, text, tables, confidence, null);
        }

        public static OcrResult empty(String message) {
            return new OcrResult(true, "", new ArrayList<>(), 0.0, message);
        }

        public static OcrResult error(String errorMessage) {
            return new OcrResult(false, null, null, 0.0, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getExtractedText() { return extractedText; }
        public List<TableCandidate> getDetectedTables() { return detectedTables; }
        public double getConfidence() { return confidence; }
        public String getErrorMessage() { return errorMessage; }
        public boolean hasDetectedTables() { return !detectedTables.isEmpty(); }
    }

    /**
     * 执行空间感知的OCR分析
     */
    private OcrResult performSpatialOcr(BufferedImage image, String filename, int pageNumber) {
        try {
            log.debug("Starting spatial OCR analysis for: {}", filename);
            
            // 获取单词级别的OCR结果（包含坐标信息）
            List<Word> words = tesseract.getWords(image, 1); // Level 1: word level
            
            if (words.isEmpty()) {
                log.warn("No words detected in image: {}", filename);
                return OcrResult.empty("No text could be extracted from the image");
            }
            
            // 使用空间分析检测表格
            List<TableCandidate> tables = detectTablesFromSpatialData(words, pageNumber);
            
            // 构建完整文本
            StringBuilder fullText = new StringBuilder();
            if (!tables.isEmpty()) {
                // 按空间位置排序所有单词
                words.sort((w1, w2) -> {
                    int yCompare = Integer.compare(w1.getBoundingBox().y, w2.getBoundingBox().y);
                    if (yCompare != 0) return yCompare;
                    return Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x);
                });
                
                // 重构文本，保持空间关系
                fullText.append(reconstructSpatialText(words, tables));
            } else {
                // 如果没有表格，使用标准OCR
                String standardText = tesseract.doOCR(image);
                fullText.append(standardText != null ? standardText : "");
            }
            
            double confidence = calculateSpatialConfidence(words);
            
            log.info("Spatial OCR completed for {}: {} words, {} tables, confidence: {:.2f}%", 
                    filename, words.size(), tables.size(), confidence * 100);
            
            return OcrResult.success(fullText.toString(), tables, confidence);
            
        } catch (TesseractException e) {
            log.error("Error in spatial OCR for: {}", filename, e);
            // 回退到标准OCR
            try {
                String fallbackText = tesseract.doOCR(image);
                return processExtractedText(fallbackText, filename);
            } catch (TesseractException fallbackError) {
                return OcrResult.error("Spatial and standard OCR both failed: " + fallbackError.getMessage());
            }
        }
    }
    
    /**
     * 使用空间数据检测表格
     */
    private List<TableCandidate> detectTablesFromSpatialData(List<Word> words, int pageNumber) {
        List<TableCandidate> tables = new ArrayList<>();
        
        if (words.size() < 6) { // 至少需要6个词才可能构成表格
            return tables;
        }
        
        // 按Y坐标分组单词（识别行）
        Map<Integer, List<Word>> rowGroups = groupWordsByRows(words);
        
        // 检测表格区域
        List<TableRegion> tableRegions = identifyTableRegions(rowGroups);
        
        for (TableRegion region : tableRegions) {
            TableCandidate table = createSpatialTableCandidate(region, pageNumber);
            if (table != null) {
                tables.add(table);
                log.debug("Detected spatial table: {} rows, {} cols, confidence: {:.2f}%, bounds: ({}, {}) - ({}, {})",
                         table.getRowCount(), table.getColumnCount(), table.getConfidence() * 100,
                         region.bounds.x, region.bounds.y, region.bounds.x + region.bounds.width, region.bounds.y + region.bounds.height);
            }
        }
        
        return tables;
    }
    
    /**
     * 按行分组单词
     */
    private Map<Integer, List<Word>> groupWordsByRows(List<Word> words) {
        Map<Integer, List<Word>> rowGroups = new TreeMap<>();
        
        final int ROW_TOLERANCE = 10; // 像素容差
        
        for (Word word : words) {
            int y = word.getBoundingBox().y;
            
            // 查找最接近的行组
            Integer bestRow = null;
            int minDistance = Integer.MAX_VALUE;
            
            for (Integer existingRow : rowGroups.keySet()) {
                int distance = Math.abs(existingRow - y);
                if (distance < minDistance && distance <= ROW_TOLERANCE) {
                    minDistance = distance;
                    bestRow = existingRow;
                }
            }
            
            if (bestRow != null) {
                rowGroups.get(bestRow).add(word);
            } else {
                rowGroups.put(y, new ArrayList<>(Arrays.asList(word)));
            }
        }
        
        return rowGroups;
    }
    
    /**
     * 识别表格区域
     */
    private List<TableRegion> identifyTableRegions(Map<Integer, List<Word>> rowGroups) {
        List<TableRegion> regions = new ArrayList<>();
        
        List<Integer> sortedRows = new ArrayList<>(rowGroups.keySet());
        Collections.sort(sortedRows);
        
        if (sortedRows.size() < 2) {
            return regions;
        }
        
        TableRegion currentRegion = null;
        List<Integer> currentRegionRows = new ArrayList<>();
        
        for (int i = 0; i < sortedRows.size(); i++) {
            Integer rowY = sortedRows.get(i);
            List<Word> rowWords = rowGroups.get(rowY);
            
            // 检查当前行是否看起来像表格行
            if (isTableLikeRow(rowWords)) {
                if (currentRegion == null) {
                    // 开始新的表格区域
                    currentRegion = new TableRegion();
                    currentRegionRows = new ArrayList<>();
                }
                currentRegionRows.add(rowY);
            } else {
                // 结束当前表格区域
                if (currentRegion != null && currentRegionRows.size() >= 2) {
                    finishTableRegion(currentRegion, currentRegionRows, rowGroups);
                    regions.add(currentRegion);
                }
                currentRegion = null;
                currentRegionRows = null;
            }
        }
        
        // 处理文档末尾的表格区域
        if (currentRegion != null && currentRegionRows != null && currentRegionRows.size() >= 2) {
            finishTableRegion(currentRegion, currentRegionRows, rowGroups);
            regions.add(currentRegion);
        }
        
        return regions;
    }
    
    /**
     * 检查行是否像表格行
     */
    private boolean isTableLikeRow(List<Word> rowWords) {
        if (rowWords.size() < 2) return false;
        
        // 按X坐标排序
        rowWords.sort((w1, w2) -> Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x));
        
        // 检查是否有规律的间隔（暗示列）
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < rowWords.size(); i++) {
            Word prev = rowWords.get(i - 1);
            Word curr = rowWords.get(i);
            int gap = curr.getBoundingBox().x - (prev.getBoundingBox().x + prev.getBoundingBox().width);
            if (gap > 10) { // 最小间隔
                gaps.add(gap);
            }
        }
        
        // 如果有多个明显的间隔，可能是表格
        if (gaps.size() >= 2) {
            return true;
        }
        
        // 检查是否包含数字（表格常有数字）
        long numberCount = rowWords.stream()
            .filter(word -> word.getText().matches(".*\\d+.*"))
            .count();
        
        return numberCount >= 2 && rowWords.size() >= 3;
    }
    
    /**
     * 完成表格区域的构建
     */
    private void finishTableRegion(TableRegion region, List<Integer> rowYs, Map<Integer, List<Word>> rowGroups) {
        // 收集所有单词
        List<Word> allWords = new ArrayList<>();
        for (Integer rowY : rowYs) {
            allWords.addAll(rowGroups.get(rowY));
        }
        
        // 计算边界框
        if (!allWords.isEmpty()) {
            int minX = allWords.stream().mapToInt(w -> w.getBoundingBox().x).min().orElse(0);
            int maxX = allWords.stream().mapToInt(w -> w.getBoundingBox().x + w.getBoundingBox().width).max().orElse(0);
            int minY = allWords.stream().mapToInt(w -> w.getBoundingBox().y).min().orElse(0);
            int maxY = allWords.stream().mapToInt(w -> w.getBoundingBox().y + w.getBoundingBox().height).max().orElse(0);
            
            region.bounds = new Rectangle(minX, minY, maxX - minX, maxY - minY);
            region.words = allWords;
            region.rowCount = rowYs.size();
            
            // 估算列数
            region.columnCount = estimateColumnsFromSpatialData(rowGroups, rowYs);
        }
    }
    
    /**
     * 从空间数据估算列数
     */
    private int estimateColumnsFromSpatialData(Map<Integer, List<Word>> rowGroups, List<Integer> rowYs) {
        int maxCols = 0;
        
        for (Integer rowY : rowYs) {
            List<Word> rowWords = rowGroups.get(rowY);
            rowWords.sort((w1, w2) -> Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x));
            
            // 基于空间间隔估算列数
            int cols = 1;
            for (int i = 1; i < rowWords.size(); i++) {
                Word prev = rowWords.get(i - 1);
                Word curr = rowWords.get(i);
                int gap = curr.getBoundingBox().x - (prev.getBoundingBox().x + prev.getBoundingBox().width);
                if (gap > 20) { // 列间隔阈值
                    cols++;
                }
            }
            maxCols = Math.max(maxCols, cols);
        }
        
        return maxCols;
    }
    
    /**
     * 创建基于空间分析的表格候选项
     */
    private TableCandidate createSpatialTableCandidate(TableRegion region, int pageNumber) {
        if (region.words.isEmpty()) return null;
        
        // 使用细胞边界检测重新构建表格内容
        String tableContent = reconstructTableWithCellBoundaries(region);
        
        TableCandidate table = new TableCandidate();
        table.setContent(tableContent);
        table.setPageNumber(pageNumber);
        table.setRowCount(region.rowCount);
        table.setColumnCount(region.columnCount);
        table.setConfidence(calculateSpatialTableConfidence(region));
        
        // 空间坐标信息
        table.setBounds(region.bounds);
        
        return table;
    }
    
    /**
     * 使用真正的单元格边界框重新构建表格内容
     */
    private String reconstructTableWithCellBoundaries(TableRegion region) {
        StringBuilder content = new StringBuilder();
        
        // 第一步：检测真正的表格网格边界框
        List<List<CellBoundary>> tableMatrix = detectTableCellBoundaries(region);
        
        // 第二步：为每个边界框提取完整的文本内容
        for (List<CellBoundary> row : tableMatrix) {
            for (int col = 0; col < row.size(); col++) {
                CellBoundary cell = row.get(col);
                String cellContent = extractCompleteTextFromBoundary(cell, region.words);
                content.append(cellContent.trim());
                
                if (col < row.size() - 1) {
                    content.append("\t"); // 使用制表符分隔列
                }
            }
            content.append("\n"); // 行结束
        }
        
        return content.toString().trim();
    }
    
    /**
     * 检测表格的真正单元格边界框矩阵
     */
    private List<List<CellBoundary>> detectTableCellBoundaries(TableRegion region) {
        List<List<CellBoundary>> tableMatrix = new ArrayList<>();
        
        // 获取表格的整体边界
        Rectangle tableBounds = region.bounds;
        
        // 检测水平分割线（行边界）
        List<Integer> rowBoundaries = detectHorizontalBoundaries(region.words, tableBounds);
        
        // 检测垂直分割线（列边界）
        List<Integer> colBoundaries = detectVerticalBoundaries(region.words, tableBounds);
        
        log.debug("Detected {} row boundaries and {} column boundaries", 
                 rowBoundaries.size(), colBoundaries.size());
        
        // 构建单元格边界框矩阵
        for (int row = 0; row < rowBoundaries.size() - 1; row++) {
            List<CellBoundary> rowCells = new ArrayList<>();
            
            int topY = rowBoundaries.get(row);
            int bottomY = rowBoundaries.get(row + 1);
            
            for (int col = 0; col < colBoundaries.size() - 1; col++) {
                int leftX = colBoundaries.get(col);
                int rightX = colBoundaries.get(col + 1);
                
                // 创建单元格边界框
                CellBoundary cell = new CellBoundary();
                cell.bounds = new Rectangle(leftX, topY, rightX - leftX, bottomY - topY);
                cell.words = new ArrayList<>(); // 暂时为空，稍后填充
                
                rowCells.add(cell);
            }
            
            tableMatrix.add(rowCells);
        }
        
        return tableMatrix;
    }
    
    /**
     * 检测水平边界（行分隔）
     */
    private List<Integer> detectHorizontalBoundaries(List<Word> words, Rectangle tableBounds) {
        Set<Integer> boundaries = new TreeSet<>();
        
        // 添加表格的顶部和底部边界
        boundaries.add(tableBounds.y);
        boundaries.add(tableBounds.y + tableBounds.height);
        
        // 分析单词的Y坐标分布
        Map<Integer, Integer> yFrequency = new HashMap<>();
        for (Word word : words) {
            int y = word.getBoundingBox().y;
            yFrequency.put(y, yFrequency.getOrDefault(y, 0) + 1);
        }
        
        // 检测行间的空白区域
        List<Integer> yPositions = words.stream()
            .mapToInt(w -> w.getBoundingBox().y)
            .distinct()
            .sorted()
            .boxed()
            .collect(Collectors.toList());
        
        final int MIN_ROW_GAP = 15; // 最小行间距
        for (int i = 0; i < yPositions.size() - 1; i++) {
            int currentY = yPositions.get(i);
            int nextY = yPositions.get(i + 1);
            
            // 计算当前行的最大高度
            int maxHeight = words.stream()
                .filter(w -> Math.abs(w.getBoundingBox().y - currentY) < 5)
                .mapToInt(w -> w.getBoundingBox().height)
                .max()
                .orElse(0);
            
            int gap = nextY - (currentY + maxHeight);
            if (gap > MIN_ROW_GAP) {
                // 在间隙中间添加边界
                boundaries.add(currentY + maxHeight + gap / 2);
            }
        }
        
        return new ArrayList<>(boundaries);
    }
    
    /**
     * 检测垂直边界（列分隔）
     */
    private List<Integer> detectVerticalBoundaries(List<Word> words, Rectangle tableBounds) {
        Set<Integer> boundaries = new TreeSet<>();
        
        // 添加表格的左边和右边边界
        boundaries.add(tableBounds.x);
        boundaries.add(tableBounds.x + tableBounds.width);
        
        // 分析单词间的垂直间隙
        List<Word> sortedWords = words.stream()
            .sorted((w1, w2) -> {
                int yCompare = Integer.compare(w1.getBoundingBox().y, w2.getBoundingBox().y);
                if (Math.abs(w1.getBoundingBox().y - w2.getBoundingBox().y) < 10) {
                    return Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x);
                }
                return yCompare;
            })
            .collect(Collectors.toList());
        
        // 按行检测列间隙
        Map<Integer, List<Word>> rowGroups = words.stream()
            .collect(Collectors.groupingBy(w -> w.getBoundingBox().y / 20 * 20)); // 容差分组
        
        final int MIN_COL_GAP = 20; // 最小列间距
        
        for (List<Word> rowWords : rowGroups.values()) {
            if (rowWords.size() < 2) continue;
            
            rowWords.sort((w1, w2) -> Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x));
            
            for (int i = 0; i < rowWords.size() - 1; i++) {
                Word current = rowWords.get(i);
                Word next = rowWords.get(i + 1);
                
                int gap = next.getBoundingBox().x - (current.getBoundingBox().x + current.getBoundingBox().width);
                
                if (gap > MIN_COL_GAP) {
                    // 在间隙中间添加边界
                    int boundaryX = current.getBoundingBox().x + current.getBoundingBox().width + gap / 2;
                    boundaries.add(boundaryX);
                }
            }
        }
        
        return new ArrayList<>(boundaries);
    }
    
    /**
     * 从边界框内提取完整的文本内容 - 核心方法
     */
    private String extractCompleteTextFromBoundary(CellBoundary cellBoundary, List<Word> allWords) {
        Rectangle bounds = cellBoundary.bounds;
        
        // 找到完全或部分重叠在边界框内的所有单词
        List<Word> wordsInBoundary = allWords.stream()
            .filter(word -> isWordInBoundary(word, bounds))
            .collect(Collectors.toList());
        
        if (wordsInBoundary.isEmpty()) {
            return "";
        }
        
        // 按空间位置排序：先按Y坐标（从上到下），再按X坐标（从左到右）
        wordsInBoundary.sort((w1, w2) -> {
            Rectangle b1 = w1.getBoundingBox();
            Rectangle b2 = w2.getBoundingBox();
            
            // 如果Y坐标相近（同一行），按X坐标排序
            if (Math.abs(b1.y - b2.y) < 10) {
                return Integer.compare(b1.x, b2.x);
            }
            
            return Integer.compare(b1.y, b2.y);
        });
        
        // 智能组合文本内容
        StringBuilder content = new StringBuilder();
        int lastY = -1;
        
        for (Word word : wordsInBoundary) {
            Rectangle wordBounds = word.getBoundingBox();
            
            if (lastY != -1 && Math.abs(wordBounds.y - lastY) > 10) {
                // 新的一行，用空格连接（避免换行符）
                if (content.length() > 0) {
                    content.append(" ");
                }
            } else if (content.length() > 0) {
                // 同一行内的单词间用空格连接
                content.append(" ");
            }
            
            content.append(word.getText());
            lastY = wordBounds.y;
        }
        
        String result = content.toString().trim();
        log.debug("Extracted from boundary {}: '{}'", bounds, result);
        
        return result;
    }
    
    /**
     * 判断单词是否在边界框内
     */
    private boolean isWordInBoundary(Word word, Rectangle boundary) {
        Rectangle wordBounds = word.getBoundingBox();
        
        // 检查重叠：单词的任何部分都在边界内
        return boundary.intersects(wordBounds);
    }
    
    
    /**
     * 检测行中的单元格边界
     */
    private List<CellBoundary> detectCellBoundariesInRow(List<Word> rowWords, int tableWidth) {
        List<CellBoundary> cells = new ArrayList<>();
        
        if (rowWords.isEmpty()) return cells;
        
        // 按X坐标排序
        rowWords.sort((w1, w2) -> Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x));
        
        // 找到所有可能的列边界
        List<Integer> columnBoundaries = findColumnBoundaries(rowWords, tableWidth);
        
        // 为每个列创建单元格边界
        for (int i = 0; i < columnBoundaries.size() - 1; i++) {
            int leftBound = columnBoundaries.get(i);
            int rightBound = columnBoundaries.get(i + 1);
            
            // 收集此列中的所有单词
            List<Word> cellWords = rowWords.stream()
                .filter(word -> word.getBoundingBox().x >= leftBound && 
                              word.getBoundingBox().x + word.getBoundingBox().width <= rightBound)
                .collect(Collectors.toList());
            
            if (!cellWords.isEmpty()) {
                CellBoundary cell = new CellBoundary();
                cell.words = cellWords;
                
                // 计算单元格边界框
                int minX = cellWords.stream().mapToInt(w -> w.getBoundingBox().x).min().orElse(leftBound);
                int maxX = cellWords.stream().mapToInt(w -> w.getBoundingBox().x + w.getBoundingBox().width).max().orElse(rightBound);
                int minY = cellWords.stream().mapToInt(w -> w.getBoundingBox().y).min().orElse(0);
                int maxY = cellWords.stream().mapToInt(w -> w.getBoundingBox().y + w.getBoundingBox().height).max().orElse(0);
                
                cell.bounds = new Rectangle(minX, minY, maxX - minX, maxY - minY);
                cells.add(cell);
            }
        }
        
        return cells;
    }
    
    /**
     * 查找列边界
     */
    private List<Integer> findColumnBoundaries(List<Word> rowWords, int tableWidth) {
        Set<Integer> boundaries = new TreeSet<>();
        
        // 添加表格左右边界
        boundaries.add(0);
        boundaries.add(tableWidth);
        
        // 基于单词间的大间隔找边界
        for (int i = 1; i < rowWords.size(); i++) {
            Word prev = rowWords.get(i - 1);
            Word curr = rowWords.get(i);
            
            int gap = curr.getBoundingBox().x - (prev.getBoundingBox().x + prev.getBoundingBox().width);
            if (gap > 20) { // 大间隔阈值
                boundaries.add(prev.getBoundingBox().x + prev.getBoundingBox().width);
                boundaries.add(curr.getBoundingBox().x);
            }
        }
        
        return new ArrayList<>(boundaries);
    }
    
    /**
     * 从单元格边界提取文本
     */
    private String extractTextFromCellBoundary(CellBoundary cell) {
        if (cell.words.isEmpty()) return "";
        
        // 按空间位置排序单词（从左到右，从上到下）
        cell.words.sort((w1, w2) -> {
            int yCompare = Integer.compare(w1.getBoundingBox().y, w2.getBoundingBox().y);
            if (Math.abs(w1.getBoundingBox().y - w2.getBoundingBox().y) < 5) {
                // 如果在同一行，按X坐标排序
                return Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x);
            }
            return yCompare;
        });
        
        StringBuilder cellText = new StringBuilder();
        int lastY = -1;
        
        for (Word word : cell.words) {
            // 如果是新行，添加空格或换行
            if (lastY != -1 && Math.abs(word.getBoundingBox().y - lastY) > 5) {
                cellText.append(" "); // 行内换行用空格连接
            } else if (cellText.length() > 0) {
                cellText.append(" ");
            }
            
            cellText.append(word.getText());
            lastY = word.getBoundingBox().y;
        }
        
        return cellText.toString();
    }
    
    /**
     * 重构空间文本
     */
    private String reconstructSpatialText(List<Word> words, List<TableCandidate> tables) {
        StringBuilder text = new StringBuilder();
        
        // 简单的重构，基于Y坐标分行
        Map<Integer, List<Word>> rowGroups = groupWordsByRows(words);
        List<Integer> sortedRows = rowGroups.keySet().stream().sorted().collect(Collectors.toList());
        
        for (Integer rowY : sortedRows) {
            List<Word> rowWords = rowGroups.get(rowY);
            rowWords.sort((w1, w2) -> Integer.compare(w1.getBoundingBox().x, w2.getBoundingBox().x));
            
            for (int i = 0; i < rowWords.size(); i++) {
                if (i > 0) text.append(" ");
                text.append(rowWords.get(i).getText());
            }
            text.append("\n");
        }
        
        return text.toString();
    }
    
    /**
     * 计算空间OCR置信度
     */
    private double calculateSpatialConfidence(List<Word> words) {
        if (words.isEmpty()) return 0.0;
        
        // 基于单词置信度计算整体置信度
        double totalConfidence = words.stream()
            .mapToDouble(word -> word.getConfidence())
            .sum();
        
        return Math.min(1.0, totalConfidence / (words.size() * 100.0));
    }
    
    /**
     * 计算空间表格置信度
     */
    private double calculateSpatialTableConfidence(TableRegion region) {
        if (region.words.isEmpty()) return 0.0;
        
        double score = 0.0;
        
        // 基于单词数量
        if (region.words.size() >= 6) score += 0.3;
        
        // 基于行数和列数
        if (region.rowCount >= 2 && region.columnCount >= 2) score += 0.4;
        
        // 基于空间结构规律性
        double spatialScore = calculateSpatialRegularity(region);
        score += spatialScore * 0.3;
        
        return Math.min(1.0, score);
    }
    
    /**
     * 计算空间规律性分数
     */
    private double calculateSpatialRegularity(TableRegion region) {
        Map<Integer, List<Word>> rowGroups = groupWordsByRows(region.words);
        
        if (rowGroups.size() < 2) return 0.0;
        
        // 检查行间距的规律性
        List<Integer> rowYs = rowGroups.keySet().stream().sorted().collect(Collectors.toList());
        List<Integer> rowGaps = new ArrayList<>();
        
        for (int i = 1; i < rowYs.size(); i++) {
            rowGaps.add(rowYs.get(i) - rowYs.get(i - 1));
        }
        
        // 计算间距的方差
        if (rowGaps.isEmpty()) return 0.0;
        
        double meanGap = rowGaps.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double variance = rowGaps.stream()
            .mapToDouble(gap -> Math.pow(gap - meanGap, 2))
            .average().orElse(0.0);
        
        // 方差越小，规律性越好
        double regularity = 1.0 / (1.0 + variance / 100.0);
        
        return Math.min(1.0, regularity);
    }
    
    /**
     * 表格候选项类 - 增强版
     */
    public static class TableCandidate {
        private String content;
        private int startLine;
        private int endLine;
        private int pageNumber;
        private int rowCount;
        private int columnCount;
        private double confidence;
        private Rectangle bounds; // 空间边界框

        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public int getStartLine() { return startLine; }
        public void setStartLine(int startLine) { this.startLine = startLine; }
        
        public int getEndLine() { return endLine; }
        public void setEndLine(int endLine) { this.endLine = endLine; }
        
        public int getPageNumber() { return pageNumber; }
        public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
        
        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }
        
        public int getColumnCount() { return columnCount; }
        public void setColumnCount(int columnCount) { this.columnCount = columnCount; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public Rectangle getBounds() { return bounds; }
        public void setBounds(Rectangle bounds) { this.bounds = bounds; }
    }
    
    /**
     * 表格区域类
     */
    private static class TableRegion {
        Rectangle bounds;
        List<Word> words;
        int rowCount;
        int columnCount;
    }
    
    /**
     * 单元格边界类
     */
    private static class CellBoundary {
        Rectangle bounds;
        List<Word> words;
    }
}