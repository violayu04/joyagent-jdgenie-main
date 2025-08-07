package com.jd.genie.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "documents")
public class Document {
    
    public enum DocumentStatus {
        PROCESSING, COMPLETED, FAILED
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "document_id", unique = true, nullable = false)
    private String documentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_base_id", nullable = false)
    private KnowledgeBase knowledgeBase;
    
    @Column(nullable = false, length = 500)
    private String filename;
    
    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;
    
    @Column(name = "file_path", length = 1000)
    private String filePath;
    
    @Column(name = "content_type", length = 100)
    private String contentType;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "content_hash")
    private String contentHash;
    
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.PROCESSING;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DocumentChunk> chunks;
}