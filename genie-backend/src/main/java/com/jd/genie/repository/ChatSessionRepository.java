package com.jd.genie.repository;

import com.jd.genie.entity.ChatSession;
import com.jd.genie.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    
    List<ChatSession> findByUserAndIsActiveTrueOrderByUpdatedAtDesc(User user);
    
    Optional<ChatSession> findBySessionIdAndUser(String sessionId, User user);
    
    Optional<ChatSession> findBySessionId(String sessionId);
}