package com.jd.genie.dto.knowledgebase;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DocumentDto {
    private String documentId;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String status;
    private String errorMessage;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}