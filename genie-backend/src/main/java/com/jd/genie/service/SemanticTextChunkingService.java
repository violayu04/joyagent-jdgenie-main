package com.jd.genie.service;

import com.jd.genie.config.VectorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 语义感知的文本分块服务
 * 解决传统分块策略破坏语义完整性的问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticTextChunkingService {
    
    private final VectorConfig vectorConfig;
    
    // 句子结束标志
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？.!?])\\s*");
    
    // 段落分隔符
    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\n\n+");
    
    // 标题模式 (markdown标题、编号等)
    private static final Pattern TITLE_PATTERN = Pattern.compile("^(#{1,6}\\s|\\d+\\.\\s|[一二三四五六七八九十]、|\\([一二三四五六七八九十]\\)|[A-Za-z]\\.\\s)");
    
    // 列表项模式
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^(\\s*[-*+]\\s|\\s*\\d+\\.\\s|\\s*[a-zA-Z]\\.\\s)");
    
    // 表格边界模式 (markdown表格和简单表格)
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^\\s*\\|[-:\\s|]*\\|\\s*$");
    // 更广泛的表格模式，包括无边框表格
    private static final Pattern TABLE_LIKE_PATTERN = Pattern.compile("^\\s*\\w+\\s*\\|\\s*\\w+.*$");
    // 表格连续行检测 - 至少3列的数据
    private static final Pattern MULTI_COLUMN_PATTERN = Pattern.compile("^\\s*[^\\|]*\\|[^\\|]*\\|[^\\|]*.*$");
    
    // 代码块模式
    private static final Pattern CODE_BLOCK_START = Pattern.compile("^\\s*```\\w*\\s*$");
    private static final Pattern CODE_BLOCK_END = Pattern.compile("^\\s*```\\s*$");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("^\\s{4,}|^\\t+"); // 缩进代码
    
    /**
     * 语义感知分块主方法
     * @param text 原始文本
     * @return 语义完整的文本块列表
     */
    public List<SemanticTextChunk> chunkTextSemantically(String text) {
        VectorConfig.Chunking config = vectorConfig.getChunking();
        
        log.info("Starting semantic chunking with target size: {} characters, overlap: {} characters, max table size: {}", 
                config.getChunkSize(), config.getChunkOverlap(), 
                config.getMaxTableChunkSize() != null ? config.getMaxTableChunkSize() : "default");
        
        // 1. 预处理：清理和规范化文本
        String cleanedText = preprocessText(text);
        
        // 2. 文档结构分析：识别段落、标题、列表等
        List<DocumentSegment> segments = analyzeDocumentStructure(cleanedText);
        
        // 3. 语义感知分块：基于结构和语义边界进行分块
        List<SemanticTextChunk> chunks = performSemanticChunking(segments, config);
        
        // 统计段落类型
        Map<SegmentType, Long> segmentTypeCounts = segments.stream()
            .collect(java.util.stream.Collectors.groupingBy(s -> s.type, java.util.stream.Collectors.counting()));
        
        log.info("Semantic chunking completed: {} chunks generated from {} segments. Segment types: {}", 
                chunks.size(), segments.size(), segmentTypeCounts);
        
        return chunks;
    }
    
    /**
     * 预处理文本：清理和规范化
     */
    private String preprocessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        return text
            // 统一换行符
            .replaceAll("\r\n", "\n")
            .replaceAll("\r", "\n")
            // 清理多余空白字符
            .replaceAll("[ \t]+", " ")
            // 规范化段落分隔（保留双换行，清理三个以上的换行）
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }
    
    /**
     * 分析文档结构：识别段落、标题、列表、表格、代码块等语义单元
     */
    private List<DocumentSegment> analyzeDocumentStructure(String text) {
        List<DocumentSegment> segments = new ArrayList<>();
        String[] lines = text.split("\n");
        
        int position = 0;
        int i = 0;
        
        while (i < lines.length) {
            String line = lines[i];
            
            // 跳过空行
            if (line.trim().isEmpty()) {
                position += line.length() + 1; // +1 for \n
                i++;
                continue;
            }
            
            // 检查代码块
            if (CODE_BLOCK_START.matcher(line).matches()) {
                CodeBlockResult codeBlock = extractCodeBlock(lines, i, position);
                segments.add(codeBlock.segment);
                i = codeBlock.endIndex + 1;
                position = codeBlock.endPosition;
                continue;
            }
            
            // 检查表格 - 使用更广泛的模式检测
            if (TABLE_ROW_PATTERN.matcher(line).matches() || 
                TABLE_LIKE_PATTERN.matcher(line).matches() ||
                MULTI_COLUMN_PATTERN.matcher(line).matches()) {
                TableResult table = extractTable(lines, i, position);
                if (table.segment != null) { // 确保提取到了有效的表格
                    segments.add(table.segment);
                    i = table.endIndex + 1;
                    position = table.endPosition;
                    continue;
                }
            }
            
            // 检查缩进代码块
            if (INLINE_CODE_PATTERN.matcher(line).matches()) {
                IndentedCodeResult indentedCode = extractIndentedCode(lines, i, position);
                segments.add(indentedCode.segment);
                i = indentedCode.endIndex + 1;
                position = indentedCode.endPosition;
                continue;
            }
            
            // 普通段落处理 - 收集到下一个空行
            ParagraphResult paragraph = extractParagraph(lines, i, position);
            DocumentSegment segment = classifyParagraph(paragraph.content, paragraph.startPosition);
            segments.add(segment);
            i = paragraph.endIndex + 1;
            position = paragraph.endPosition;
        }
        
        // 合并相关的segments（如连续的列表项）
        return mergeRelatedSegments(segments);
    }
    
    /**
     * 段落分类：识别段落类型
     */
    private DocumentSegment classifyParagraph(String paragraph, int startPos) {
        DocumentSegment segment = new DocumentSegment();
        segment.content = paragraph;
        segment.startPosition = startPos;
        segment.endPosition = startPos + paragraph.length();
        
        if (TITLE_PATTERN.matcher(paragraph).find()) {
            segment.type = SegmentType.TITLE;
            segment.priority = Priority.HIGH; // 标题不应被分割
        } else if (LIST_ITEM_PATTERN.matcher(paragraph).find()) {
            segment.type = SegmentType.LIST_ITEM;
            segment.priority = Priority.MEDIUM;
        } else if (paragraph.contains("\n") && paragraph.lines().count() > 3) {
            segment.type = SegmentType.MULTI_LINE_CONTENT;
            segment.priority = Priority.MEDIUM;
        } else {
            segment.type = SegmentType.PARAGRAPH;
            segment.priority = Priority.LOW;
        }
        
        return segment;
    }
    
    /**
     * 提取代码块
     */
    private CodeBlockResult extractCodeBlock(String[] lines, int startIndex, int startPosition) {
        StringBuilder codeContent = new StringBuilder();
        int currentPos = startPosition;
        int i = startIndex;
        
        // 添加开始标记
        codeContent.append(lines[i]).append("\n");
        currentPos += lines[i].length() + 1;
        i++;
        
        // 查找结束标记
        while (i < lines.length) {
            String line = lines[i];
            codeContent.append(line).append("\n");
            currentPos += line.length() + 1;
            
            if (CODE_BLOCK_END.matcher(line).matches()) {
                break;
            }
            i++;
        }
        
        DocumentSegment segment = new DocumentSegment();
        segment.content = codeContent.toString().trim();
        segment.type = SegmentType.CODE_BLOCK;
        segment.priority = Priority.HIGH; // 代码块不应被分割
        segment.startPosition = startPosition;
        segment.endPosition = currentPos - 1; // -1 to exclude final \n
        
        return new CodeBlockResult(segment, i, currentPos - 1);
    }
    
    /**
     * 提取表格 - 增强版，支持多行单元格重构
     */
    private TableResult extractTable(String[] lines, int startIndex, int startPosition) {
        int currentPos = startPosition;
        int i = startIndex;
        int tableRowCount = 0;
        List<String> rawTableLines = new ArrayList<>();
        
        // 收集连续的表格行
        while (i < lines.length) {
            String line = lines[i];
            
            // 检查是否是表格行
            if (TABLE_ROW_PATTERN.matcher(line).matches() || 
                TABLE_LIKE_PATTERN.matcher(line).matches() ||
                MULTI_COLUMN_PATTERN.matcher(line).matches() ||
                TABLE_SEPARATOR_PATTERN.matcher(line).matches()) {
                
                rawTableLines.add(line);
                currentPos += line.length() + 1;
                tableRowCount++;
                i++;
                
            } else if (line.trim().isEmpty()) {
                // 空行可能是表格的一部分，先检查下一行
                int nextNonEmptyIndex = i + 1;
                while (nextNonEmptyIndex < lines.length && lines[nextNonEmptyIndex].trim().isEmpty()) {
                    nextNonEmptyIndex++;
                }
                
                // 如果下一行非空且仍是表格行，包含空行
                if (nextNonEmptyIndex < lines.length && 
                    (TABLE_ROW_PATTERN.matcher(lines[nextNonEmptyIndex]).matches() ||
                     TABLE_LIKE_PATTERN.matcher(lines[nextNonEmptyIndex]).matches() ||
                     MULTI_COLUMN_PATTERN.matcher(lines[nextNonEmptyIndex]).matches())) {
                    
                    // 包含空行到下一个表格行
                    while (i <= nextNonEmptyIndex) {
                        if (!lines[i].trim().isEmpty()) {
                            rawTableLines.add(lines[i]);
                        }
                        currentPos += lines[i].length() + 1;
                        i++;
                    }
                    i--; // 回退一个，因为循环会递增
                    
                } else {
                    // 表格结束
                    break;
                }
            } else {
                // 非表格行，表格结束
                break;
            }
        }
        
        // 至少需要2行才认为是有效表格
        if (tableRowCount < 2) {
            log.debug("Table detection failed: only {} rows found", tableRowCount);
            return new TableResult(null, startIndex, startPosition);
        }
        
        // 重构表格以处理多行单元格
        String reconstructedTable = reconstructTableCells(rawTableLines);
        
        DocumentSegment segment = new DocumentSegment();
        segment.content = reconstructedTable;
        segment.type = SegmentType.TABLE;
        segment.priority = Priority.HIGH; // 表格不应被分割
        segment.startPosition = startPosition;
        segment.endPosition = currentPos - 1; // -1 to exclude final \n
        
        log.info("Extracted and reconstructed table with {} rows, {} characters", tableRowCount, segment.content.length());
        
        return new TableResult(segment, i - 1, currentPos - 1);
    }
    
    /**
     * 重构表格单元格 - 将多行单元格内容合并
     */
    private String reconstructTableCells(List<String> tableLines) {
        if (tableLines.isEmpty()) {
            return "";
        }
        
        // 分析表格结构
        TableStructure structure = analyzeTableStructure(tableLines);
        if (structure == null) {
            // 如果无法分析结构，返回原始内容
            return String.join("\n", tableLines);
        }
        
        // 重构表格
        List<List<String>> reconstructedRows = new ArrayList<>();
        
        for (int rowIndex = 0; rowIndex < structure.rowCount; rowIndex++) {
            List<String> reconstructedRow = new ArrayList<>();
            
            for (int colIndex = 0; colIndex < structure.columnCount; colIndex++) {
                StringBuilder cellContent = new StringBuilder();
                
                // 收集该单元格的所有内容片段
                for (CellFragment fragment : structure.cellFragments) {
                    if (fragment.logicalRow == rowIndex && fragment.logicalCol == colIndex) {
                        if (cellContent.length() > 0) {
                            cellContent.append(" ");
                        }
                        cellContent.append(fragment.content.trim());
                    }
                }
                
                reconstructedRow.add(cellContent.toString());
            }
            
            reconstructedRows.add(reconstructedRow);
        }
        
        // 转换为字符串格式
        StringBuilder result = new StringBuilder();
        for (List<String> row : reconstructedRows) {
            result.append("| ");
            for (String cell : row) {
                result.append(cell).append(" | ");
            }
            result.append("\n");
        }
        
        log.debug("Table reconstruction completed: {} logical rows, {} columns", 
                 structure.rowCount, structure.columnCount);
        
        return result.toString().trim();
    }
    
    /**
     * 分析表格结构
     */
    private TableStructure analyzeTableStructure(List<String> tableLines) {
        try {
            TableStructure structure = new TableStructure();
            structure.cellFragments = new ArrayList<>();
            
            // 分析列数（基于第一行）
            String firstLine = tableLines.get(0);
            String[] firstRowCells = firstLine.split("\\|");
            structure.columnCount = Math.max(2, firstRowCells.length - (firstLine.startsWith("|") ? 2 : 1));
            
            int logicalRowIndex = 0;
            int physicalRowIndex = 0;
            
            for (String line : tableLines) {
                // 跳过分隔符行
                if (TABLE_SEPARATOR_PATTERN.matcher(line).matches()) {
                    physicalRowIndex++;
                    continue;
                }
                
                String[] cells = line.split("\\|", -1);
                int startCol = line.startsWith("|") ? 1 : 0;
                int endCol = Math.min(startCol + structure.columnCount, cells.length);
                
                // 检查这是否是新的逻辑行还是续行
                boolean isNewRow = isNewLogicalRow(line, physicalRowIndex, tableLines);
                
                if (isNewRow && physicalRowIndex > 0) {
                    logicalRowIndex++;
                }
                
                // 提取每个单元格的内容
                for (int colIndex = 0; colIndex < structure.columnCount && (startCol + colIndex) < endCol; colIndex++) {
                    String cellContent = cells[startCol + colIndex].trim();
                    
                    if (!cellContent.isEmpty()) {
                        CellFragment fragment = new CellFragment();
                        fragment.content = cellContent;
                        fragment.logicalRow = logicalRowIndex;
                        fragment.logicalCol = colIndex;
                        fragment.physicalRow = physicalRowIndex;
                        structure.cellFragments.add(fragment);
                    }
                }
                
                physicalRowIndex++;
            }
            
            structure.rowCount = logicalRowIndex + 1;
            
            log.debug("Table structure analyzed: {} physical rows -> {} logical rows, {} columns", 
                     tableLines.size(), structure.rowCount, structure.columnCount);
            
            return structure;
            
        } catch (Exception e) {
            log.warn("Failed to analyze table structure: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 判断是否是新的逻辑行
     */
    private boolean isNewLogicalRow(String currentLine, int physicalRowIndex, List<String> allLines) {
        if (physicalRowIndex == 0) {
            return true; // 第一行总是新行
        }
        
        // 如果第一个单元格有内容，通常是新行
        String[] cells = currentLine.split("\\|", -1);
        int startCol = currentLine.startsWith("|") ? 1 : 0;
        
        if (startCol < cells.length) {
            String firstCell = cells[startCol].trim();
            // 如果第一个单元格有实质内容，认为是新行
            if (!firstCell.isEmpty() && firstCell.length() > 2) {
                return true;
            }
        }
        
        // 检查是否大部分单元格都有内容（新行的特征）
        int nonEmptyCells = 0;
        int totalCells = 0;
        
        for (int i = startCol; i < cells.length && totalCells < 5; i++, totalCells++) {
            if (!cells[i].trim().isEmpty()) {
                nonEmptyCells++;
            }
        }
        
        // 如果超过一半的单元格有内容，认为是新行
        return totalCells > 0 && (double) nonEmptyCells / totalCells > 0.5;
    }
    
    /**
     * 提取缩进代码块
     */
    private IndentedCodeResult extractIndentedCode(String[] lines, int startIndex, int startPosition) {
        StringBuilder codeContent = new StringBuilder();
        int currentPos = startPosition;
        int i = startIndex;
        
        // 收集连续的缩进行
        while (i < lines.length && 
               (INLINE_CODE_PATTERN.matcher(lines[i]).matches() || lines[i].trim().isEmpty())) {
            codeContent.append(lines[i]).append("\n");
            currentPos += lines[i].length() + 1;
            i++;
        }
        
        DocumentSegment segment = new DocumentSegment();
        segment.content = codeContent.toString().trim();
        segment.type = SegmentType.INDENTED_CODE;
        segment.priority = Priority.HIGH; // 代码块不应被分割
        segment.startPosition = startPosition;
        segment.endPosition = currentPos - 1; // -1 to exclude final \n
        
        return new IndentedCodeResult(segment, i - 1, currentPos - 1);
    }
    
    /**
     * 提取普通段落 - 改进的逻辑，更好地处理中文文本和连续内容
     */
    private ParagraphResult extractParagraph(String[] lines, int startIndex, int startPosition) {
        StringBuilder paragraphContent = new StringBuilder();
        int currentPos = startPosition;
        int i = startIndex;
        int consecutiveEmptyLines = 0;
        
        // 收集到真正的段落结束
        while (i < lines.length) {
            String line = lines[i];
            
            // 处理空行 - 只有连续2个或更多空行才结束段落
            if (line.trim().isEmpty()) {
                consecutiveEmptyLines++;
                if (consecutiveEmptyLines >= 2) {
                    break;
                }
                // 单个空行可能只是格式问题，继续收集
                currentPos += line.length() + 1;
                i++;
                continue;
            } else {
                consecutiveEmptyLines = 0;
            }
            
            // 遇到明确的特殊结构停止
            if (CODE_BLOCK_START.matcher(line).matches() || 
                TABLE_ROW_PATTERN.matcher(line).matches() ||
                INLINE_CODE_PATTERN.matcher(line).matches()) {
                break;
            }
            
            // 检查是否是新标题（通过缩进和特殊字符判断）
            if (isNewSection(line, i > startIndex)) {
                break;
            }
            
            paragraphContent.append(line);
            // 对于中文文本，行末不加换行符，用空格连接
            if (needsSpaceConnection(line)) {
                paragraphContent.append(" ");
            } else {
                paragraphContent.append("\n");
            }
            currentPos += line.length() + 1;
            i++;
        }
        
        return new ParagraphResult(
            paragraphContent.toString().trim(), 
            startPosition, 
            i - 1, 
            currentPos - 1
        );
    }
    
    /**
     * 判断是否需要用空格连接行（主要针对中文和数字混合内容）
     */
    private boolean needsSpaceConnection(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        
        // 如果行末是中文字符，且不是标点符号，则用空格连接
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        return Character.getType(lastChar) == Character.OTHER_LETTER && 
               !"，。；：！？、".contains(String.valueOf(lastChar));
    }
    
    /**
     * 判断是否是新章节开始
     */
    private boolean isNewSection(String line, boolean hasContent) {
        if (!hasContent) return false;
        
        String trimmed = line.trim();
        
        // 检查是否是数字标题（如 "1. " "（一）" 等）
        if (trimmed.matches("^\\d+[.、]\\s+.*") || 
            trimmed.matches("^[（（][一二三四五六七八九十\\d+][）］]\\s*.*") ||
            trimmed.matches("^[一二三四五六七八九十]+[、.]\\s+.*")) {
            return true;
        }
        
        // 检查是否是明显的标题（全大写或特殊格式）
        if (trimmed.matches("^[A-Z][A-Z\\s]+$") || 
            trimmed.matches("^【.*】.*") ||
            trimmed.matches("^\\*+\\s+.*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 合并相关的segments（如连续的列表项）
     */
    private List<DocumentSegment> mergeRelatedSegments(List<DocumentSegment> segments) {
        List<DocumentSegment> merged = new ArrayList<>();
        
        for (int i = 0; i < segments.size(); i++) {
            DocumentSegment current = segments.get(i);
            
            if (current.type == SegmentType.LIST_ITEM) {
                // 收集连续的列表项
                StringBuilder listContent = new StringBuilder(current.content);
                int startPos = current.startPosition;
                int endPos = current.endPosition;
                
                for (int j = i + 1; j < segments.size(); j++) {
                    DocumentSegment next = segments.get(j);
                    if (next.type == SegmentType.LIST_ITEM) {
                        listContent.append("\n\n").append(next.content);
                        endPos = next.endPosition;
                        i++; // 跳过已合并的项
                    } else {
                        break;
                    }
                }
                
                DocumentSegment listSegment = new DocumentSegment();
                listSegment.content = listContent.toString();
                listSegment.type = SegmentType.LIST_GROUP;
                listSegment.priority = Priority.HIGH; // 列表组不应被分割
                listSegment.startPosition = startPos;
                listSegment.endPosition = endPos;
                
                merged.add(listSegment);
            } else {
                merged.add(current);
            }
        }
        
        return merged;
    }
    
    /**
     * 执行语义感知分块
     */
    private List<SemanticTextChunk> performSemanticChunking(List<DocumentSegment> segments, VectorConfig.Chunking config) {
        List<SemanticTextChunk> chunks = new ArrayList<>();
        
        StringBuilder currentChunk = new StringBuilder();
        List<DocumentSegment> currentSegments = new ArrayList<>();
        int chunkIndex = 0;
        int startPos = 0;
        
        for (DocumentSegment segment : segments) {
            int potentialSize = currentChunk.length() + segment.content.length() + 
                              (currentChunk.length() > 0 ? 2 : 0); // +2 for \n\n separator
            
            boolean shouldStartNewChunk = shouldStartNewChunk(
                currentChunk, segment, potentialSize, config.getChunkSize()
            );
            
            // 特殊处理：表格优先级处理
            if (segment.type == SegmentType.TABLE) {
                // 大表格且当前chunk不为空，强制创建新的chunk给表格
                if (segment.content.length() > config.getChunkSize() && currentChunk.length() > 0) {
                    log.info("Large table detected ({} chars), creating dedicated chunk", segment.content.length());
                    shouldStartNewChunk = true;
                }
                // 即使是中等大小的表格，如果当前chunk已经有一些内容，也优先给表格创建新chunk
                else if (segment.content.length() > config.getChunkSize() * 0.6 && 
                         currentChunk.length() > config.getChunkSize() * 0.4) {
                    log.info("Medium table detected ({} chars), creating dedicated chunk to preserve table integrity", 
                            segment.content.length());
                    shouldStartNewChunk = true;
                }
            }
            
            if (shouldStartNewChunk && currentChunk.length() > 0) {
                // 创建当前chunk
                SemanticTextChunk chunk = createChunk(
                    currentChunk.toString().trim(), 
                    currentSegments, 
                    chunkIndex++, 
                    startPos
                );
                chunks.add(chunk);
                
                // 处理overlap
                String overlapContent = calculateOverlap(chunks, config.getChunkOverlap());
                currentChunk = new StringBuilder(overlapContent);
                currentSegments = new ArrayList<>();
                startPos = segment.startPosition - overlapContent.length();
            }
            
            // 添加当前segment
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(segment.content);
            currentSegments.add(segment);
        }
        
        // 处理最后一个chunk
        if (currentChunk.length() > 0) {
            SemanticTextChunk chunk = createChunk(
                currentChunk.toString().trim(), 
                currentSegments, 
                chunkIndex, 
                startPos
            );
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * 判断是否应该开始新的chunk
     */
    private boolean shouldStartNewChunk(StringBuilder currentChunk, DocumentSegment segment, 
                                      int potentialSize, int maxChunkSize) {
        
        // 如果当前chunk为空，不开始新chunk
        if (currentChunk.length() == 0) {
            return false;
        }
        
        // 获取配置
        VectorConfig.Chunking config = vectorConfig.getChunking();
        
        // 对于表格，使用更大的chunk大小限制
        int effectiveMaxSize = maxChunkSize;
        if (segment.type == SegmentType.TABLE) {
            effectiveMaxSize = config.getMaxTableChunkSize() != null ? 
                config.getMaxTableChunkSize() : maxChunkSize * 2;
            log.debug("Using table-specific max chunk size: {} for table segment", effectiveMaxSize);
        }
        
        // 如果segment是高优先级（如标题、列表组、表格），且会超出大小限制，开始新chunk
        // 但对于表格给予更多空间
        if (segment.priority == Priority.HIGH && potentialSize > effectiveMaxSize) {
            // 对于表格，如果当前chunk很小，尝试包含在当前chunk中
            if (segment.type == SegmentType.TABLE && currentChunk.length() < maxChunkSize * 0.3) {
                log.debug("Table segment size {} would exceed limit, but current chunk is small, trying to fit", 
                         segment.content.length());
                return false;
            }
            return true;
        }
        
        // 如果大小超出限制太多，必须开始新chunk
        // 但对表格更宽松
        double sizeLimitMultiplier = (segment.type == SegmentType.TABLE) ? 2.0 : 1.5;
        if (potentialSize > effectiveMaxSize * sizeLimitMultiplier) {
            return true;
        }
        
        // 如果是标题，优先独立成块或开始新块
        if (segment.type == SegmentType.TITLE && currentChunk.length() > maxChunkSize * 0.6) {
            return true;
        }
        
        // 正常大小检查 - 对表格使用有效的最大大小
        return potentialSize > effectiveMaxSize;
    }
    
    /**
     * 创建语义文本块
     */
    private SemanticTextChunk createChunk(String content, List<DocumentSegment> segments, 
                                        int chunkIndex, int startPos) {
        SemanticTextChunk chunk = new SemanticTextChunk();
        chunk.content = content;
        chunk.chunkIndex = chunkIndex;
        chunk.startPos = startPos;
        chunk.endPos = startPos + content.length();
        chunk.tokenCount = estimateTokenCount(content);
        chunk.segments = new ArrayList<>(segments);
        
        // 分析chunk的语义特征
        chunk.hasTitle = segments.stream().anyMatch(s -> s.type == SegmentType.TITLE);
        chunk.hasListItems = segments.stream().anyMatch(s -> 
            s.type == SegmentType.LIST_ITEM || s.type == SegmentType.LIST_GROUP);
        chunk.hasTable = segments.stream().anyMatch(s -> s.type == SegmentType.TABLE);
        chunk.hasCodeBlock = segments.stream().anyMatch(s -> 
            s.type == SegmentType.CODE_BLOCK || s.type == SegmentType.INDENTED_CODE);
        chunk.segmentCount = segments.size();
        
        // 特殊日志记录表格chunk
        if (chunk.hasTable) {
            log.info("Created chunk {} with table content: {} characters, {} segments", 
                    chunkIndex, content.length(), segments.size());
        }
        
        return chunk;
    }
    
    /**
     * 计算chunk间的重叠内容
     */
    private String calculateOverlap(List<SemanticTextChunk> existingChunks, int overlapSize) {
        if (existingChunks.isEmpty() || overlapSize <= 0) {
            return "";
        }
        
        SemanticTextChunk lastChunk = existingChunks.get(existingChunks.size() - 1);
        String lastContent = lastChunk.content;
        
        // 尝试按句子边界创建overlap
        String[] sentences = SENTENCE_END.split(lastContent);
        if (sentences.length <= 1) {
            // 如果没有句子边界，按单词创建overlap
            return getLastWords(lastContent, overlapSize);
        }
        
        StringBuilder overlap = new StringBuilder();
        for (int i = sentences.length - 1; i >= 0; i--) {
            String sentence = sentences[i].trim();
            if (overlap.length() + sentence.length() <= overlapSize) {
                if (overlap.length() > 0) {
                    overlap.insert(0, sentence + "。 ");
                } else {
                    overlap.insert(0, sentence);
                }
            } else {
                break;
            }
        }
        
        return overlap.toString();
    }
    
    /**
     * 获取文本的最后几个单词（用于overlap）
     */
    private String getLastWords(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        
        // 尝试在句子边界处截断
        String[] sentences = SENTENCE_END.split(text);
        if (sentences.length > 1) {
            String lastSentence = sentences[sentences.length - 1];
            if (lastSentence.length() <= maxLength) {
                return lastSentence;
            }
        }
        
        // 按单词边界截断
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = words.length - 1; i >= 0; i--) {
            String word = words[i];
            if (result.length() + word.length() + 1 <= maxLength) {
                if (result.length() > 0) {
                    result.insert(0, " ");
                }
                result.insert(0, word);
            } else {
                break;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 估算token数量
     */
    private int estimateTokenCount(String text) {
        // 改进的token估算：考虑中英文混合文本
        int chineseChars = 0;
        int englishWords = 0;
        int punctuation = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseChars++;
            } else if (Character.isLetterOrDigit(c)) {
                // 英文字符，后续统计单词
            } else if (Character.isWhitespace(c)) {
                // 空白字符
            } else {
                punctuation++;
            }
        }
        
        // 统计英文单词
        String englishText = text.replaceAll("[\\u4e00-\\u9fa5]", " ");
        String[] words = englishText.trim().split("\\s+");
        for (String word : words) {
            if (!word.isEmpty() && word.matches(".*[a-zA-Z0-9].*")) {
                englishWords++;
            }
        }
        
        // token估算：中文字符1:1，英文单词1:1，标点符号0.5:1
        return chineseChars + englishWords + (punctuation / 2);
    }
    
    // 内部数据类
    
    /**
     * 文档片段
     */
    public static class DocumentSegment {
        public String content;
        public SegmentType type;
        public Priority priority;
        public int startPosition;
        public int endPosition;
    }
    
    /**
     * 片段类型
     */
    public enum SegmentType {
        TITLE,              // 标题
        PARAGRAPH,          // 普通段落
        LIST_ITEM,          // 列表项
        LIST_GROUP,         // 列表组
        MULTI_LINE_CONTENT, // 多行内容
        TABLE,              // 表格
        CODE_BLOCK,         // 代码块 (```)
        INDENTED_CODE       // 缩进代码块
    }
    
    /**
     * 优先级（影响分块策略）
     */
    public enum Priority {
        HIGH,    // 高优先级，不应被分割
        MEDIUM,  // 中等优先级，尽量保持完整
        LOW      // 低优先级，可以灵活分割
    }
    
    /**
     * 语义文本块
     */
    public static class SemanticTextChunk {
        private String content;
        private Integer chunkIndex;
        private Integer startPos;
        private Integer endPos;
        private Integer tokenCount;
        private List<DocumentSegment> segments;
        private Boolean hasTitle;
        private Boolean hasListItems;
        private Boolean hasTable;
        private Boolean hasCodeBlock;
        private Integer segmentCount;
        
        // Getters and setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public Integer getStartPos() { return startPos; }
        public void setStartPos(Integer startPos) { this.startPos = startPos; }
        
        public Integer getEndPos() { return endPos; }
        public void setEndPos(Integer endPos) { this.endPos = endPos; }
        
        public Integer getTokenCount() { return tokenCount; }
        public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
        
        public List<DocumentSegment> getSegments() { return segments; }
        public void setSegments(List<DocumentSegment> segments) { this.segments = segments; }
        
        public Boolean getHasTitle() { return hasTitle; }
        public void setHasTitle(Boolean hasTitle) { this.hasTitle = hasTitle; }
        
        public Boolean getHasListItems() { return hasListItems; }
        public void setHasListItems(Boolean hasListItems) { this.hasListItems = hasListItems; }
        
        public Boolean getHasTable() { return hasTable; }
        public void setHasTable(Boolean hasTable) { this.hasTable = hasTable; }
        
        public Boolean getHasCodeBlock() { return hasCodeBlock; }
        public void setHasCodeBlock(Boolean hasCodeBlock) { this.hasCodeBlock = hasCodeBlock; }
        
        public Integer getSegmentCount() { return segmentCount; }
        public void setSegmentCount(Integer segmentCount) { this.segmentCount = segmentCount; }
    }
    
    // 辅助结果类
    
    /**
     * 代码块提取结果
     */
    private static class CodeBlockResult {
        public final DocumentSegment segment;
        public final int endIndex;
        public final int endPosition;
        
        public CodeBlockResult(DocumentSegment segment, int endIndex, int endPosition) {
            this.segment = segment;
            this.endIndex = endIndex;
            this.endPosition = endPosition;
        }
    }
    
    /**
     * 表格提取结果
     */
    private static class TableResult {
        public final DocumentSegment segment;
        public final int endIndex;
        public final int endPosition;
        
        public TableResult(DocumentSegment segment, int endIndex, int endPosition) {
            this.segment = segment;
            this.endIndex = endIndex;
            this.endPosition = endPosition;
        }
    }
    
    /**
     * 缩进代码块提取结果
     */
    private static class IndentedCodeResult {
        public final DocumentSegment segment;
        public final int endIndex;
        public final int endPosition;
        
        public IndentedCodeResult(DocumentSegment segment, int endIndex, int endPosition) {
            this.segment = segment;
            this.endIndex = endIndex;
            this.endPosition = endPosition;
        }
    }
    
    /**
     * 段落提取结果
     */
    private static class ParagraphResult {
        public final String content;
        public final int startPosition;
        public final int endIndex;
        public final int endPosition;
        
        public ParagraphResult(String content, int startPosition, int endIndex, int endPosition) {
            this.content = content;
            this.startPosition = startPosition;
            this.endIndex = endIndex;
            this.endPosition = endPosition;
        }
    }
    
    /**
     * 表格结构
     */
    private static class TableStructure {
        int rowCount;
        int columnCount;
        List<CellFragment> cellFragments;
    }
    
    /**
     * 单元格片段
     */
    private static class CellFragment {
        String content;
        int logicalRow;
        int logicalCol;
        int physicalRow;
    }
}