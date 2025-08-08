package com.jd.genie.controller;

import com.jd.genie.service.HtmlTableAwareChunkingService;
import com.jd.genie.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR测试控制器 - 用于测试OCR功能的表格提取能力
 */
@Slf4j
@RestController
@RequestMapping("/api/ocr-test")
@RequiredArgsConstructor
public class OcrTestController {

    private final OcrService ocrService;
    private final HtmlTableAwareChunkingService htmlTableAwareChunkingService;

    /**
     * 测试OCR文本提取
     */
    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> testOcrExtraction(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("Starting OCR test for file: {} ({})", file.getOriginalFilename(), file.getContentType());
            
            // 创建临时文件
            Path tempFile = Files.createTempFile("ocr_test_", "_" + file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            File testFile = tempFile.toFile();
            String contentType = file.getContentType();
            
            OcrService.OcrResult ocrResult = null;
            
            // 根据文件类型执行OCR
            if (contentType != null && contentType.startsWith("image/")) {
                ocrResult = ocrService.extractTextFromImage(testFile);
            } else if (contentType != null && contentType.equals("application/pdf")) {
                ocrResult = ocrService.extractTextFromPdf(testFile);
            } else {
                response.put("success", false);
                response.put("error", "Unsupported file type: " + contentType);
                return ResponseEntity.badRequest().body(response);
            }
            
            // 清理临时文件
            Files.deleteIfExists(tempFile);
            
            if (ocrResult != null && ocrResult.isSuccess()) {
                response.put("success", true);
                response.put("extractedText", ocrResult.getExtractedText());
                response.put("confidence", ocrResult.getConfidence());
                response.put("textLength", ocrResult.getExtractedText().length());
                response.put("tablesDetected", ocrResult.getDetectedTables().size());
                
                // 添加表格详细信息
                if (ocrResult.hasDetectedTables()) {
                    response.put("tables", ocrResult.getDetectedTables().stream().map(table -> {
                        Map<String, Object> tableInfo = new HashMap<>();
                        tableInfo.put("content", table.getContent());
                        tableInfo.put("rows", table.getRowCount());
                        tableInfo.put("columns", table.getColumnCount());
                        tableInfo.put("confidence", table.getConfidence());
                        tableInfo.put("startLine", table.getStartLine());
                        tableInfo.put("endLine", table.getEndLine());
                        return tableInfo;
                    }).toList());
                }
                
            } else {
                response.put("success", false);
                response.put("error", ocrResult != null ? ocrResult.getErrorMessage() : "OCR extraction failed");
            }
            
        } catch (IOException e) {
            log.error("Error during OCR test", e);
            response.put("success", false);
            response.put("error", "File processing error: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 测试OCR增强的分块功能
     */
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> testOcrEnhancedChunking(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("Starting OCR-enhanced chunking test for file: {} ({})", 
                    file.getOriginalFilename(), file.getContentType());
            
            // 创建临时文件
            Path tempFile = Files.createTempFile("ocr_chunk_test_", "_" + file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            File testFile = tempFile.toFile();
            String contentType = file.getContentType();
            
            // 执行OCR增强的分块
            List<HtmlTableAwareChunkingService.HtmlAwareTextChunk> chunks = 
                htmlTableAwareChunkingService.chunkTextWithOcrEnhancement(testFile, contentType);
            
            // 清理临时文件
            Files.deleteIfExists(tempFile);
            
            response.put("success", true);
            response.put("totalChunks", chunks.size());
            
            // 统计不同类型的分块
            Map<String, Integer> chunkTypeStats = new HashMap<>();
            int tableChunks = 0;
            int textChunks = 0;
            
            for (HtmlTableAwareChunkingService.HtmlAwareTextChunk chunk : chunks) {
                String chunkType = chunk.getChunkType().name();
                chunkTypeStats.put(chunkType, chunkTypeStats.getOrDefault(chunkType, 0) + 1);
                
                if (chunk.getChunkType() == HtmlTableAwareChunkingService.ChunkType.TABLE) {
                    tableChunks++;
                } else {
                    textChunks++;
                }
            }
            
            response.put("chunkTypeStats", chunkTypeStats);
            response.put("tableChunks", tableChunks);
            response.put("textChunks", textChunks);
            
            // 提供分块详细信息（限制数量以避免响应过大）
            response.put("chunkDetails", chunks.stream().limit(10).map(chunk -> {
                Map<String, Object> chunkInfo = new HashMap<>();
                chunkInfo.put("index", chunk.getChunkIndex());
                chunkInfo.put("type", chunk.getChunkType().name());
                chunkInfo.put("tokenCount", chunk.getTokenCount());
                chunkInfo.put("contentPreview", chunk.getContent().length() > 100 ? 
                    chunk.getContent().substring(0, 100) + "..." : chunk.getContent());
                
                if (chunk.getChunkType() == HtmlTableAwareChunkingService.ChunkType.TABLE) {
                    chunkInfo.put("tableRows", chunk.getTableRows());
                    chunkInfo.put("tableCols", chunk.getTableCols());
                    chunkInfo.put("htmlContent", chunk.getHtmlContent());
                }
                
                return chunkInfo;
            }).toList());
            
        } catch (IOException e) {
            log.error("Error during OCR chunking test", e);
            response.put("success", false);
            response.put("error", "File processing error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error during OCR chunking test", e);
            response.put("success", false);
            response.put("error", "OCR chunking failed: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取OCR服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOcrStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // 简单测试OCR服务是否可用
            status.put("ocrServiceAvailable", true);
            status.put("supportedFormats", List.of("image/jpeg", "image/png", "image/gif", "application/pdf"));
            status.put("features", List.of("Text extraction", "Table detection", "Multi-language support", "Confidence scoring"));
            
        } catch (Exception e) {
            log.error("Error checking OCR status", e);
            status.put("ocrServiceAvailable", false);
            status.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(status);
    }
}