package com.jd.genie.dto.knowledgebase;

import lombok.Data;

@Data
public class SearchResultDto {
    private String chunkId;
    private String content;
    private double score;
    private Integer chunkIndex;
    private Integer tokenCount;
    private String documentId;
    private String filename;
}