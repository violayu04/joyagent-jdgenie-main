package com.jd.genie.controller;

import com.jd.genie.agent.dto.RawFileRequest;
import com.jd.genie.service.IRawFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * 原始文件上传控制器
 * 专门处理"上传文档功能"，直接将原始文档内容发送给LLM，不进行任何文本提取或分析
 * 后端作为纯粹的管道
 */
@Slf4j
@RestController
@RequestMapping("/api/raw-file")
public class RawFileController {

    @Autowired
    private IRawFileService rawFileService;

    /**
     * 上传原始文件并直接发送给LLM处理
     * 这是"上传文档功能"的核心实现，确保后端不进行任何文本提取或分析
     * 
     * @param file 上传的文件
     * @param description 文件描述（可选）
     * @return 包含LLM处理结果的响应
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadRawFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false, defaultValue = "用户上传的文档") String description) {
        
        String requestId = UUID.randomUUID().toString();
        log.info("{} 收到原始文件上传请求: 文件名={}, 大小={}字节", requestId, file.getOriginalFilename(), file.getSize());

        try {
            // 读取文件的原始字节内容
            byte[] fileBytes = file.getBytes();
            
            // 将原始字节编码为base64
            String base64Content = Base64.getEncoder().encodeToString(fileBytes);
            
            // 构建请求对象
            RawFileRequest rawFileRequest = RawFileRequest.builder()
                    .requestId(requestId)
                    .fileName(file.getOriginalFilename())
                    .description(description)
                    .rawBytes(base64Content)
                    .build();

            // 直接发送给LLM处理
            String llmResponse = rawFileService.processRawFile(rawFileRequest);
            
            if (llmResponse != null) {
                log.info("{} 原始文件处理成功: {}", requestId, file.getOriginalFilename());
                return ResponseEntity.ok()
                        .body(new UploadResponse(true, "文件上传成功，已发送给LLM处理", llmResponse, file.getOriginalFilename()));
            } else {
                log.error("{} 原始文件处理失败: {}", requestId, file.getOriginalFilename());
                return ResponseEntity.ok()
                        .body(new UploadResponse(false, "文件上传失败，LLM处理出错", null, file.getOriginalFilename()));
            }
            
        } catch (IOException e) {
            log.error("{} 读取文件内容失败: {}", requestId, file.getOriginalFilename(), e);
            return ResponseEntity.ok()
                    .body(new UploadResponse(false, "文件读取失败: " + e.getMessage(), null, file.getOriginalFilename()));
        } catch (Exception e) {
            log.error("{} 处理文件时发生未知错误: {}", requestId, file.getOriginalFilename(), e);
            return ResponseEntity.ok()
                    .body(new UploadResponse(false, "处理文件时发生错误: " + e.getMessage(), null, file.getOriginalFilename()));
        }
    }

    /**
     * 上传响应DTO
     */
    public static class UploadResponse {
        private boolean success;
        private String message;
        private String llmResponse;
        private String fileName;

        public UploadResponse(boolean success, String message, String llmResponse, String fileName) {
            this.success = success;
            this.message = message;
            this.llmResponse = llmResponse;
            this.fileName = fileName;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getLlmResponse() { return llmResponse; }
        public void setLlmResponse(String llmResponse) { this.llmResponse = llmResponse; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }
}
