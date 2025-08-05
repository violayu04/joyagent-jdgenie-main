package com.jd.genie.repository;

import com.jd.genie.entity.ChatMessage;
import com.jd.genie.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    List<ChatMessage> findByChatSessionOrderByMessageOrderAsc(ChatSession chatSession);
    
    List<ChatMessage> findByChatSessionOrderByCreatedAtAsc(ChatSession chatSession);
}