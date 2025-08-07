package com.jd.genie.repository;

import com.jd.genie.entity.Document;
import com.jd.genie.entity.KnowledgeBase;
import com.jd.genie.entity.Document.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    List<Document> findByKnowledgeBaseOrderByCreatedAtDesc(KnowledgeBase knowledgeBase);
    
    List<Document> findByKnowledgeBaseAndStatus(KnowledgeBase knowledgeBase, DocumentStatus status);
    
    Optional<Document> findByDocumentId(String documentId);
    
    Optional<Document> findByKnowledgeBaseAndContentHash(KnowledgeBase knowledgeBase, String contentHash);
    
    @Query("SELECT d FROM Document d WHERE d.knowledgeBase.user.id = :userId AND d.status = :status")
    List<Document> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") DocumentStatus status);
    
    long countByKnowledgeBaseAndStatus(KnowledgeBase knowledgeBase, DocumentStatus status);
    
    @Query("SELECT COUNT(d) FROM Document d WHERE d.knowledgeBase = :knowledgeBase")
    long countByKnowledgeBase(@Param("knowledgeBase") KnowledgeBase knowledgeBase);
}