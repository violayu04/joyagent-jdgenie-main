package com.jd.genie.repository;

import com.jd.genie.entity.Document;
import com.jd.genie.entity.KnowledgeBase;
import com.jd.genie.entity.Document.DocumentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    @EntityGraph(attributePaths = {"knowledgeBase", "knowledgeBase.user"})
    List<Document> findByKnowledgeBaseOrderByCreatedAtDesc(KnowledgeBase knowledgeBase);
    
    @EntityGraph(attributePaths = {"knowledgeBase", "knowledgeBase.user"})
    List<Document> findByKnowledgeBaseAndStatus(KnowledgeBase knowledgeBase, DocumentStatus status);
    
    @EntityGraph(attributePaths = {"knowledgeBase", "knowledgeBase.user"})
    Optional<Document> findByDocumentId(String documentId);
    
    @EntityGraph(attributePaths = {"knowledgeBase", "knowledgeBase.user"})
    Optional<Document> findByKnowledgeBaseAndContentHash(KnowledgeBase knowledgeBase, String contentHash);
    
    @EntityGraph(attributePaths = {"knowledgeBase", "knowledgeBase.user"})
    @Query("SELECT d FROM Document d WHERE d.knowledgeBase.user.id = :userId AND d.status = :status")
    List<Document> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") DocumentStatus status);
    
    long countByKnowledgeBaseAndStatus(KnowledgeBase knowledgeBase, DocumentStatus status);
    
    @Query("SELECT COUNT(d) FROM Document d WHERE d.knowledgeBase = :knowledgeBase")
    long countByKnowledgeBase(@Param("knowledgeBase") KnowledgeBase knowledgeBase);
}