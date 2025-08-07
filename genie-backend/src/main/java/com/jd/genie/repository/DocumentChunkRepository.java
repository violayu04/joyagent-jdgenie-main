package com.jd.genie.repository;

import com.jd.genie.entity.Document;
import com.jd.genie.entity.DocumentChunk;
import com.jd.genie.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    
    List<DocumentChunk> findByDocumentOrderByChunkIndex(Document document);
    
    Optional<DocumentChunk> findByChunkId(String chunkId);
    
    Optional<DocumentChunk> findByEmbeddingId(String embeddingId);
    
    List<DocumentChunk> findByEmbeddingIdIn(List<String> embeddingIds);
    
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.document.knowledgeBase.id = :knowledgeBaseId")
    List<DocumentChunk> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    
    @Query("SELECT COUNT(dc) FROM DocumentChunk dc WHERE dc.document = :document")
    long countByDocument(@Param("document") Document document);
    
    void deleteByDocument(Document document);
    
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.content = :content AND dc.document.knowledgeBase = :knowledgeBase")
    Optional<DocumentChunk> findByContentAndKnowledgeBase(@Param("content") String content, @Param("knowledgeBase") KnowledgeBase knowledgeBase);
}