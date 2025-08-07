package com.jd.genie.repository;

import com.jd.genie.entity.KnowledgeBase;
import com.jd.genie.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    
    List<KnowledgeBase> findByUserAndIsActiveTrueOrderByUpdatedAtDesc(User user);
    
    Optional<KnowledgeBase> findByIdAndUser(Long id, User user);
    
    List<KnowledgeBase> findByUserAndNameContainingIgnoreCaseAndIsActiveTrue(User user, String name);
    
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.user = :user AND kb.isActive = true ORDER BY kb.documentCount DESC, kb.updatedAt DESC")
    List<KnowledgeBase> findActiveKnowledgeBasesByDocumentCount(@Param("user") User user);
    
    boolean existsByUserAndNameAndIsActive(User user, String name, Boolean isActive);
}