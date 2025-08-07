package com.jd.genie.dto.knowledgebase;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseDto {
    private Long id;
    private String name;
    private String description;
    private Integer documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}