package com.jd.genie.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "chunk_id", unique = true, nullable = false)
    private String chunkId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;
    
    @Column(name = "start_pos")
    private Integer startPos;
    
    @Column(name = "end_pos")
    private Integer endPos;
    
    @Column(name = "token_count")
    private Integer tokenCount;
    
    @Column(name = "embedding_id")
    private String embeddingId; // 向量数据库中的ID
    
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}