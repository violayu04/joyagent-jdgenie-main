package com.jd.genie.service;

import com.jd.genie.config.VectorConfig;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.io.File;
import java.awt.Rectangle;

/**
 * HTML感知的文本分块服务，专门用于保护表格结构的完整性
 */
@Slf4j
@Service
public class HtmlTableAwareChunkingService {
    
    private final VectorConfig vectorConfig;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final SemanticTextChunkingService semanticChunkingService;
    private final OcrService ocrService;
    
    // Markdown表格检测模式
    private static final Pattern MARKDOWN_TABLE_PATTERN = Pattern.compile(
        "\\|[^\\r\\n]*\\|[\\s]*\\r?\\n\\|[\\s]*:?-+:?[\\s]*\\|", 
        Pattern.MULTILINE
    );
    
    public HtmlTableAwareChunkingService(VectorConfig vectorConfig, 
                                       SemanticTextChunkingService semanticChunkingService,
                                       OcrService ocrService) {
        this.vectorConfig = vectorConfig;
        this.semanticChunkingService = semanticChunkingService;
        this.ocrService = ocrService;
        
        // 初始化CommonMark解析器，支持GFM表格扩展
        List<Extension> extensions = Arrays.asList(TablesExtension.create());
        this.markdownParser = Parser.builder()
                .extensions(extensions)
                .build();
        this.htmlRenderer = HtmlRenderer.builder()
                .extensions(extensions)
                .build();
    }
    
    /**
     * HTML感知的文本分块
     */
    public List<HtmlAwareTextChunk> chunkTextWithHtmlAwareness(String text) {
        log.info("Starting HTML-aware chunking for text of {} characters", text.length());
        
        try {
            // 1. 转换为HTML
            String htmlContent = convertToHtml(text);
            
            // 2. 从HTML中提取分块
            List<HtmlAwareTextChunk> chunks = extractChunksFromHtml(htmlContent);
            
            log.info("HTML-aware chunking completed: {} chunks generated", chunks.size());
            return chunks;
            
        } catch (Exception e) {
            log.error("Failed to perform HTML-aware chunking, falling back to semantic chunking", e);
            // 出错时回退到语义分块
            return fallbackToSemanticChunking(text);
        }
    }

    /**
     * OCR增强的文本分块 - 支持图像和PDF文件
     */
    public List<HtmlAwareTextChunk> chunkTextWithOcrEnhancement(File file, String contentType) {
        log.info("Starting OCR-enhanced chunking for file: {} ({})", file.getName(), contentType);
        
        try {
            OcrService.OcrResult ocrResult = null;
            
            // 根据文件类型选择OCR处理方式
            if (contentType != null) {
                if (contentType.startsWith("image/")) {
                    ocrResult = ocrService.extractTextFromImage(file);
                } else if (contentType.equals("application/pdf")) {
                    ocrResult = ocrService.extractTextFromPdf(file);
                }
            }
            
            if (ocrResult == null || !ocrResult.isSuccess()) {
                log.warn("OCR extraction failed for file: {}, falling back to standard processing", file.getName());
                return new ArrayList<>();
            }
            
            // 使用OCR提取的文本进行分块
            String extractedText = ocrResult.getExtractedText();
            if (extractedText == null || extractedText.trim().isEmpty()) {
                log.warn("No text extracted from file: {}", file.getName());
                return new ArrayList<>();
            }
            
            // 基于OCR结果创建增强的分块
            List<HtmlAwareTextChunk> chunks = createOcrEnhancedChunks(extractedText, ocrResult.getDetectedTables());
            
            log.info("OCR-enhanced chunking completed for {}: {} chunks, confidence: {:.2f}%", 
                    file.getName(), chunks.size(), ocrResult.getConfidence() * 100);
            
            return chunks;
            
        } catch (Exception e) {
            log.error("Failed to perform OCR-enhanced chunking for file: {}", file.getName(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 基于OCR结果创建增强的文本分块，使用行级关联
     */
    private List<HtmlAwareTextChunk> createOcrEnhancedChunks(String extractedText, List<OcrService.TableCandidate> detectedTables) {
        List<HtmlAwareTextChunk> chunks = new ArrayList<>();
        
        if (detectedTables.isEmpty()) {
            // 如果没有检测到表格，使用标准文本分块
            return chunkTextWithHtmlAwareness(extractedText);
        }
        
        // 使用行级关联处理表格和文本
        chunks.addAll(createRowAssociatedChunks(extractedText, detectedTables));
        
        return chunks;
    }

    /**
     * 获取表格所占用的行号集合
     */
    private Set<Integer> getTableLineNumbers(List<OcrService.TableCandidate> tables) {
        Set<Integer> tableLines = new HashSet<>();
        for (OcrService.TableCandidate table : tables) {
            for (int line = table.getStartLine(); line <= table.getEndLine(); line++) {
                tableLines.add(line);
            }
        }
        return tableLines;
    }

    /**
     * 基于OCR表格候选项创建文本分块（使用空间坐标信息）
     */
    private HtmlAwareTextChunk createOcrTableChunk(OcrService.TableCandidate table, int chunkIndex, int startPos) {
        String tableContent = table.getContent();
        int tokenCount = estimateTokenCount(tableContent);
        
        // 检查表格大小
        VectorConfig.Chunking chunkingConfig = vectorConfig.getChunking();
        int maxTableSize = chunkingConfig.getMaxTableChunkSize();
        
        if (tokenCount > maxTableSize) {
            log.warn("Spatial OCR table chunk size ({} tokens) exceeds max table chunk size ({}), " +
                    "but preserving as single chunk to maintain structure", tokenCount, maxTableSize);
        }
        
        HtmlAwareTextChunk chunk = new HtmlAwareTextChunk();
        chunk.setContent(tableContent);
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartPos(startPos);
        chunk.setEndPos(startPos + tableContent.length());
        chunk.setTokenCount(tokenCount);
        chunk.setChunkType(ChunkType.TABLE);
        chunk.setTableRows(table.getRowCount());
        chunk.setTableCols(table.getColumnCount());
        
        // 使用空间坐标信息增强表格HTML转换
        String htmlContent = convertSpatialOcrTableToHtml(tableContent, table);
        chunk.setHtmlContent(htmlContent);
        
        // 添加空间坐标元数据
        if (table.getBounds() != null) {
            chunk.setSpatialBounds(table.getBounds());
            log.debug("Created spatial OCR table chunk: {} rows, {} cols, {} tokens, bounds: ({}, {}) - ({}, {}), confidence: {:.2f}%", 
                     table.getRowCount(), table.getColumnCount(), tokenCount,
                     table.getBounds().x, table.getBounds().y, 
                     table.getBounds().x + table.getBounds().width, table.getBounds().y + table.getBounds().height,
                     table.getConfidence() * 100);
        } else {
            log.debug("Created OCR table chunk: {} rows, {} cols, {} tokens, confidence: {:.2f}%", 
                     table.getRowCount(), table.getColumnCount(), tokenCount, table.getConfidence() * 100);
        }
        
        return chunk;
    }

    /**
     * 将OCR识别的表格文本转换为HTML格式
     */
    private String convertOcrTableToHtml(String tableContent) {
        if (tableContent == null || tableContent.trim().isEmpty()) {
            return "<table></table>";
        }
        
        StringBuilder htmlTable = new StringBuilder("<table>");
        String[] rows = tableContent.split("\n");
        
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i].trim();
            if (row.isEmpty()) continue;
            
            htmlTable.append("<tr>");
            
            // 尝试多种分割方式
            String[] cells;
            if (row.contains("|")) {
                cells = row.split("\\|");
            } else if (row.contains("\t")) {
                cells = row.split("\t");
            } else {
                cells = row.split("\\s{2,}"); // 两个或更多空格作为分隔符
            }
            
            String cellTag = (i == 0) ? "th" : "td"; // 第一行作为表头
            
            for (String cell : cells) {
                String cleanCell = cell.trim();
                if (!cleanCell.isEmpty()) {
                    htmlTable.append("<").append(cellTag).append(">")
                             .append(cleanCell)
                             .append("</").append(cellTag).append(">");
                }
            }
            
            htmlTable.append("</tr>");
        }
        
        htmlTable.append("</table>");
        return htmlTable.toString();
    }
    
    /**
     * 使用空间坐标信息增强的HTML表格转换
     */
    private String convertSpatialOcrTableToHtml(String tableContent, OcrService.TableCandidate table) {
        if (tableContent == null || tableContent.trim().isEmpty()) {
            return "<table></table>";
        }
        
        StringBuilder htmlTable = new StringBuilder("<table");
        
        // 添加空间坐标作为HTML属性
        if (table.getBounds() != null) {
            java.awt.Rectangle bounds = table.getBounds();
            htmlTable.append(" data-bounds=\"x:")
                     .append(bounds.x)
                     .append(",y:")
                     .append(bounds.y)
                     .append(",w:")
                     .append(bounds.width)
                     .append(",h:")
                     .append(bounds.height)
                     .append("\"");
        }
        
        htmlTable.append(" data-confidence=\"").append(table.getConfidence()).append("\"");
        htmlTable.append(">");
        
        String[] rows = tableContent.split("\n");
        
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i].trim();
            if (row.isEmpty()) continue;
            
            htmlTable.append("<tr>");
            
            // 使用制表符作为主要分隔符（来自空间OCR）
            String[] cells;
            if (row.contains("\t")) {
                cells = row.split("\t");
            } else if (row.contains("|")) {
                cells = row.split("\\|");
            } else {
                cells = row.split("\\s{2,}"); // 两个或更多空格作为分隔符
            }
            
            String cellTag = (i == 0) ? "th" : "td"; // 第一行作为表头
            
            for (String cell : cells) {
                String cleanCell = cell.trim();
                if (!cleanCell.isEmpty()) {
                    htmlTable.append("<").append(cellTag).append(">")
                             .append(cleanCell)
                             .append("</").append(cellTag).append(">");
                }
            }
            
            htmlTable.append("</tr>");
        }
        
        htmlTable.append("</table>");
        return htmlTable.toString();
    }
    
    /**
     * 自动检测输入类型并转换为HTML
     */
    private String convertToHtml(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }
        
        String trimmedInput = input.trim();
        
        // 检查是否已经是HTML
        if (trimmedInput.startsWith("<html") || trimmedInput.startsWith("<!DOCTYPE")) {
            log.debug("Input detected as HTML document");
            return trimmedInput;
        }
        
        // 检查是否包含HTML标签
        if (trimmedInput.contains("<table") || trimmedInput.contains("<div") || trimmedInput.contains("<p")) {
            log.debug("Input detected as HTML fragment");
            return "<html><body>" + trimmedInput + "</body></html>";
        }
        
        // 检查是否是Markdown（特别是包含表格）
        if (MARKDOWN_TABLE_PATTERN.matcher(trimmedInput).find()) {
            log.debug("Input detected as Markdown with tables");
            return convertMarkdownToHtml(trimmedInput);
        }
        
        // 默认按纯文本处理，转换为简单HTML
        log.debug("Input detected as plain text");
        return "<html><body><p>" + trimmedInput.replace("\n\n", "</p><p>").replace("\n", "<br>") + "</p></body></html>";
    }
    
    /**
     * 使用CommonMark转换Markdown到HTML
     */
    private String convertMarkdownToHtml(String markdown) {
        try {
            org.commonmark.node.Node document = markdownParser.parse(markdown);
            String html = htmlRenderer.render(document);
            return "<html><body>" + html + "</body></html>";
        } catch (Exception e) {
            log.warn("Failed to parse Markdown, treating as plain text", e);
            return "<html><body><pre>" + markdown + "</pre></body></html>";
        }
    }
    
    /**
     * 从HTML中提取分块
     */
    private List<HtmlAwareTextChunk> extractChunksFromHtml(String htmlContent) {
        List<HtmlAwareTextChunk> chunks = new ArrayList<>();
        
        Document doc = Jsoup.parse(htmlContent);
        Elements allElements = doc.body().children();
        
        int chunkIndex = 0;
        int currentPos = 0;
        
        for (Element element : allElements) {
            if ("table".equals(element.tagName())) {
                // 处理表格：作为单独的完整分块
                HtmlAwareTextChunk tableChunk = createTableChunk(element, chunkIndex++, currentPos);
                chunks.add(tableChunk);
                currentPos += tableChunk.getContent().length();
                
            } else {
                // 处理其他元素：使用递归分块
                String elementText = element.text();
                if (!elementText.trim().isEmpty()) {
                    List<HtmlAwareTextChunk> elementChunks = processNonTableElement(
                        element, elementText, chunkIndex, currentPos);
                    chunks.addAll(elementChunks);
                    
                    // 更新索引和位置
                    for (HtmlAwareTextChunk chunk : elementChunks) {
                        chunkIndex++;
                        currentPos += chunk.getContent().length();
                    }
                }
            }
        }
        
        // 如果没有找到任何结构化内容，使用语义分块处理整个文本
        if (chunks.isEmpty()) {
            String plainText = doc.text();
            if (!plainText.trim().isEmpty()) {
                return fallbackToSemanticChunking(plainText);
            }
        }
        
        return chunks;
    }
    
    /**
     * 创建表格分块
     */
    private HtmlAwareTextChunk createTableChunk(Element tableElement, int chunkIndex, int startPos) {
        String tableText = tableElement.text();
        String tableHtml = tableElement.outerHtml();
        
        // 分析表格结构
        Elements rows = tableElement.select("tr");
        Elements headers = tableElement.select("th");
        int rowCount = rows.size();
        int colCount = headers.isEmpty() ? 
            (rows.isEmpty() ? 0 : rows.first().select("td, th").size()) : 
            headers.size();
        
        // 估算token数量（中英文混合）
        int tokenCount = estimateTokenCount(tableText);
        
        // 检查是否超过表格专用的最大大小
        VectorConfig.Chunking chunkingConfig = vectorConfig.getChunking();
        int maxTableSize = chunkingConfig.getMaxTableChunkSize();
        
        if (tokenCount > maxTableSize) {
            log.warn("Table chunk size ({} tokens) exceeds max table chunk size ({}), " +
                    "but preserving as single chunk to maintain structure", tokenCount, maxTableSize);
        }
        
        HtmlAwareTextChunk chunk = new HtmlAwareTextChunk();
        chunk.setContent(tableText);
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartPos(startPos);
        chunk.setEndPos(startPos + tableText.length());
        chunk.setTokenCount(tokenCount);
        chunk.setChunkType(ChunkType.TABLE);
        chunk.setHtmlContent(tableHtml);
        chunk.setTableRows(rowCount);
        chunk.setTableCols(colCount);
        
        log.debug("Created table chunk: {} rows, {} cols, {} tokens", rowCount, colCount, tokenCount);
        
        return chunk;
    }
    
    /**
     * 处理非表格元素
     */
    private List<HtmlAwareTextChunk> processNonTableElement(Element element, String elementText, 
                                                          int baseChunkIndex, int basePos) {
        List<HtmlAwareTextChunk> chunks = new ArrayList<>();
        
        VectorConfig.Chunking chunkingConfig = vectorConfig.getChunking();
        int chunkSize = chunkingConfig.getChunkSize();
        int tokenCount = estimateTokenCount(elementText);
        
        if (tokenCount <= chunkSize) {
            // 元素足够小，作为单个分块
            HtmlAwareTextChunk chunk = new HtmlAwareTextChunk();
            chunk.setContent(elementText);
            chunk.setChunkIndex(baseChunkIndex);
            chunk.setStartPos(basePos);
            chunk.setEndPos(basePos + elementText.length());
            chunk.setTokenCount(tokenCount);
            chunk.setChunkType(determineContentType(element));
            chunk.setHtmlContent(element.outerHtml());
            
            chunks.add(chunk);
        } else {
            // 元素太大，需要进一步分割
            List<String> subChunks = splitLargeContent(elementText, chunkSize);
            
            int currentPos = basePos;
            for (int i = 0; i < subChunks.size(); i++) {
                String subChunk = subChunks.get(i);
                int subTokenCount = estimateTokenCount(subChunk);
                
                HtmlAwareTextChunk chunk = new HtmlAwareTextChunk();
                chunk.setContent(subChunk);
                chunk.setChunkIndex(baseChunkIndex + i);
                chunk.setStartPos(currentPos);
                chunk.setEndPos(currentPos + subChunk.length());
                chunk.setTokenCount(subTokenCount);
                chunk.setChunkType(ChunkType.TEXT);
                
                chunks.add(chunk);
                currentPos += subChunk.length();
            }
        }
        
        return chunks;
    }
    
    /**
     * 分割大型内容
     */
    private List<String> splitLargeContent(String content, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        
        // 使用语义分块服务的逻辑进行分割
        // 这里简化实现，实际可以复用更复杂的分割逻辑
        int approxChunkLength = (int) (maxChunkSize * 3.5); // 估算字符数
        
        if (content.length() <= approxChunkLength) {
            chunks.add(content);
            return chunks;
        }
        
        // 按段落分割
        String[] paragraphs = content.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            if (currentChunk.length() + paragraph.length() > approxChunkLength && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder(paragraph);
            } else {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            }
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    /**
     * 确定内容类型
     */
    private ChunkType determineContentType(Element element) {
        String tagName = element.tagName().toLowerCase();
        
        switch (tagName) {
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                return ChunkType.HEADER;
            case "ul":
            case "ol":
            case "li":
                return ChunkType.LIST;
            case "pre":
            case "code":
                return ChunkType.CODE;
            case "blockquote":
                return ChunkType.QUOTE;
            default:
                return ChunkType.TEXT;
        }
    }
    
    /**
     * 估算Token数量（针对中英文混合文本）
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        // 中文字符数量（每个中文字符约1个token）
        long chineseCharCount = text.chars().filter(ch -> ch >= 0x4E00 && ch <= 0x9FFF).count();
        
        // 英文单词数量（每个单词约0.75个token）
        String[] englishWords = text.replaceAll("[\\u4E00-\\u9FFF]", " ").split("\\s+");
        long englishWordCount = Arrays.stream(englishWords)
                .filter(word -> !word.trim().isEmpty())
                .count();
        
        return (int) (chineseCharCount + englishWordCount * 0.75);
    }
    
    /**
     * 回退到语义分块
     */
    private List<HtmlAwareTextChunk> fallbackToSemanticChunking(String text) {
        log.info("Falling back to semantic chunking for text processing");
        
        var semanticChunks = semanticChunkingService.chunkTextSemantically(text);
        List<HtmlAwareTextChunk> htmlChunks = new ArrayList<>();
        
        for (var semanticChunk : semanticChunks) {
            HtmlAwareTextChunk htmlChunk = new HtmlAwareTextChunk();
            htmlChunk.setContent(semanticChunk.getContent());
            htmlChunk.setChunkIndex(semanticChunk.getChunkIndex());
            htmlChunk.setStartPos(semanticChunk.getStartPos());
            htmlChunk.setEndPos(semanticChunk.getEndPos());
            htmlChunk.setTokenCount(semanticChunk.getTokenCount());
            htmlChunk.setChunkType(ChunkType.TEXT);
            
            htmlChunks.add(htmlChunk);
        }
        
        return htmlChunks;
    }
    
    /**
     * 创建基于行关联的文本分块
     */
    private List<HtmlAwareTextChunk> createRowAssociatedChunks(String extractedText, List<OcrService.TableCandidate> detectedTables) {
        List<HtmlAwareTextChunk> chunks = new ArrayList<>();
        
        // 按行分割文本并构建行级映射
        String[] lines = extractedText.split("\n");
        Map<Integer, RowContext> rowContextMap = buildRowContextMap(lines, detectedTables);
        
        int chunkIndex = 0;
        
        // 处理每个表格，使用行级关联
        for (OcrService.TableCandidate table : detectedTables) {
            List<HtmlAwareTextChunk> tableChunks = createRowAssociatedTableChunks(table, rowContextMap, chunkIndex);
            chunks.addAll(tableChunks);
            chunkIndex += tableChunks.size();
        }
        
        // 处理非表格文本
        List<HtmlAwareTextChunk> nonTableChunks = createNonTableChunks(rowContextMap, chunkIndex);
        chunks.addAll(nonTableChunks);
        
        // 重新计算位置
        int currentPos = 0;
        for (HtmlAwareTextChunk chunk : chunks) {
            chunk.setStartPos(currentPos);
            chunk.setEndPos(currentPos + chunk.getContent().length());
            currentPos = chunk.getEndPos();
        }
        
        return chunks;
    }
    
    /**
     * 构建行上下文映射
     */
    private Map<Integer, RowContext> buildRowContextMap(String[] lines, List<OcrService.TableCandidate> detectedTables) {
        Map<Integer, RowContext> rowContextMap = new HashMap<>();
        
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1; // 行号从1开始
            RowContext context = new RowContext();
            context.lineNumber = lineNumber;
            context.content = lines[i];
            context.isTableLine = false;
            
            // 检查是否属于表格
            for (OcrService.TableCandidate table : detectedTables) {
                if (lineNumber >= table.getStartLine() && lineNumber <= table.getEndLine()) {
                    context.isTableLine = true;
                    context.associatedTable = table;
                    context.relativeRowInTable = lineNumber - table.getStartLine() + 1;
                    break;
                }
            }
            
            rowContextMap.put(lineNumber, context);
        }
        
        return rowContextMap;
    }
    
    /**
     * 创建基于行关联的表格分块
     */
    private List<HtmlAwareTextChunk> createRowAssociatedTableChunks(OcrService.TableCandidate table, 
                                                                    Map<Integer, RowContext> rowContextMap, 
                                                                    int baseChunkIndex) {
        List<HtmlAwareTextChunk> chunks = new ArrayList<>();
        
        // 收集表格的所有行内容
        List<String> tableRows = new ArrayList<>();
        
        for (int lineNum = table.getStartLine(); lineNum <= table.getEndLine(); lineNum++) {
            RowContext context = rowContextMap.get(lineNum);
            if (context != null && context.isTableLine) {
                String rowContent = context.content.trim();
                if (!rowContent.isEmpty()) {
                    tableRows.add(rowContent);
                }
            }
        }
        
        // 使用增强的行关联创建表格分块
        String finalTableContent = processRowAssociatedTableContent(tableRows);
        
        HtmlAwareTextChunk tableChunk = new HtmlAwareTextChunk();
        tableChunk.setContent(finalTableContent);
        tableChunk.setChunkIndex(baseChunkIndex);
        tableChunk.setTokenCount(estimateTokenCount(finalTableContent));
        tableChunk.setChunkType(ChunkType.TABLE);
        tableChunk.setTableRows(tableRows.size());
        tableChunk.setTableCols(table.getColumnCount());
        
        // 使用空间坐标信息增强表格HTML转换
        String htmlContent = convertSpatialOcrTableToHtml(finalTableContent, table);
        tableChunk.setHtmlContent(htmlContent);
        
        // 添加空间坐标元数据
        if (table.getBounds() != null) {
            tableChunk.setSpatialBounds(table.getBounds());
        }
        
        // 添加行关联元数据
        tableChunk.setRowAssociationMetadata(createRowAssociationMetadata(tableRows, table));
        
        chunks.add(tableChunk);
        
        log.debug("Created row-associated table chunk: {} rows, {} cols, {} tokens, spatial bounds: {}", 
                 tableRows.size(), table.getColumnCount(), tableChunk.getTokenCount(),
                 table.getBounds() != null ? "present" : "none");
        
        return chunks;
    }
    
    /**
     * 处理基于行关联的表格内容
     */
    private String processRowAssociatedTableContent(List<String> tableRows) {
        StringBuilder processedContent = new StringBuilder();
        
        for (int i = 0; i < tableRows.size(); i++) {
            String row = tableRows.get(i);
            
            // 清理和规范化行内容
            String cleanedRow = normalizeTableRow(row);
            processedContent.append(cleanedRow);
            
            if (i < tableRows.size() - 1) {
                processedContent.append("\n");
            }
        }
        
        return processedContent.toString();
    }
    
    /**
     * 规范化表格行内容
     */
    private String normalizeTableRow(String row) {
        if (row == null || row.trim().isEmpty()) {
            return "";
        }
        
        // 移除多余的空格但保持列结构
        String normalized = row.trim();
        
        // 统一分隔符 - 优先使用制表符
        if (normalized.contains("\t")) {
            return normalized;
        } else if (normalized.contains("|")) {
            return normalized.replaceAll("\\s*\\|\\s*", "\t");
        } else {
            // 将多个空格转换为制表符
            return normalized.replaceAll("\\s{2,}", "\t");
        }
    }
    
    /**
     * 创建行关联元数据
     */
    private Map<String, Object> createRowAssociationMetadata(List<String> tableRows, OcrService.TableCandidate table) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("totalRows", tableRows.size());
        metadata.put("tableConfidence", table.getConfidence());
        metadata.put("spatialBounds", table.getBounds());
        metadata.put("hasMultilineContent", checkForMultilineContent(tableRows));
        return metadata;
    }
    
    /**
     * 检查是否有多行内容
     */
    private boolean checkForMultilineContent(List<String> tableRows) {
        for (String row : tableRows) {
            if (row.contains("\n") || row.length() > 100) { // 长行可能包含换行
                return true;
            }
        }
        return false;
    }
    
    /**
     * 创建非表格分块
     */
    private List<HtmlAwareTextChunk> createNonTableChunks(Map<Integer, RowContext> rowContextMap, int baseChunkIndex) {
        List<HtmlAwareTextChunk> chunks = new ArrayList<>();
        
        StringBuilder nonTableContent = new StringBuilder();
        
        // 收集所有非表格行
        List<Integer> sortedLines = rowContextMap.keySet().stream()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        
        for (Integer lineNum : sortedLines) {
            RowContext context = rowContextMap.get(lineNum);
            if (!context.isTableLine && !context.content.trim().isEmpty()) {
                nonTableContent.append(context.content).append("\n");
            }
        }
        
        if (nonTableContent.length() > 0) {
            // 使用标准HTML感知分块处理非表格内容
            List<HtmlAwareTextChunk> textChunks = chunkTextWithHtmlAwareness(nonTableContent.toString());
            
            // 调整分块索引
            for (int i = 0; i < textChunks.size(); i++) {
                textChunks.get(i).setChunkIndex(baseChunkIndex + i);
            }
            
            chunks.addAll(textChunks);
        }
        
        return chunks;
    }
    
    /**
     * HTML感知的文本分块结果类
     */
    public static class HtmlAwareTextChunk {
        private String content;
        private int chunkIndex;
        private int startPos;
        private int endPos;
        private int tokenCount;
        private ChunkType chunkType;
        private String htmlContent; // 原始HTML内容
        private Integer tableRows;  // 表格行数（仅表格类型）
        private Integer tableCols;  // 表格列数（仅表格类型）
        private java.awt.Rectangle spatialBounds; // 空间边界框（仅空间OCR）
        private Map<String, Object> rowAssociationMetadata; // 行关联元数据
        
        // Getters and setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public int getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public int getStartPos() { return startPos; }
        public void setStartPos(int startPos) { this.startPos = startPos; }
        
        public int getEndPos() { return endPos; }
        public void setEndPos(int endPos) { this.endPos = endPos; }
        
        public int getTokenCount() { return tokenCount; }
        public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
        
        public ChunkType getChunkType() { return chunkType; }
        public void setChunkType(ChunkType chunkType) { this.chunkType = chunkType; }
        
        public String getHtmlContent() { return htmlContent; }
        public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
        
        public Integer getTableRows() { return tableRows; }
        public void setTableRows(Integer tableRows) { this.tableRows = tableRows; }
        
        public Integer getTableCols() { return tableCols; }
        public void setTableCols(Integer tableCols) { this.tableCols = tableCols; }
        
        public java.awt.Rectangle getSpatialBounds() { return spatialBounds; }
        public void setSpatialBounds(java.awt.Rectangle spatialBounds) { this.spatialBounds = spatialBounds; }
        
        public Map<String, Object> getRowAssociationMetadata() { return rowAssociationMetadata; }
        public void setRowAssociationMetadata(Map<String, Object> rowAssociationMetadata) { this.rowAssociationMetadata = rowAssociationMetadata; }
    }
    
    /**
     * 分块类型枚举
     */
    public enum ChunkType {
        TABLE,    // 表格
        TEXT,     // 普通文本
        HEADER,   // 标题
        LIST,     // 列表
        CODE,     // 代码块
        QUOTE     // 引用块
    }
    
    /**
     * 行上下文类 - 用于行级关联处理
     */
    private static class RowContext {
        int lineNumber;
        String content;
        boolean isTableLine;
        OcrService.TableCandidate associatedTable;
        int relativeRowInTable;
    }
}