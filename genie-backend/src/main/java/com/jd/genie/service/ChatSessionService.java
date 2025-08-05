package com.jd.genie.service;

import com.jd.genie.entity.ChatMessage;
import com.jd.genie.entity.ChatSession;
import com.jd.genie.entity.User;
import com.jd.genie.repository.ChatMessageRepository;
import com.jd.genie.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public List<ChatSession> getUserSessions(User user) {
        return chatSessionRepository.findByUserAndIsActiveTrueOrderByUpdatedAtDesc(user);
    }

    public Optional<ChatSession> getSessionByIdAndUser(String sessionId, User user) {
        return chatSessionRepository.findBySessionIdAndUser(sessionId, user);
    }

    public ChatSession createSession(User user, String title) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setTitle(title != null && !title.trim().isEmpty() ? title : "新对话");
        session.setUser(user);
        session.setIsActive(true);
        
        return chatSessionRepository.save(session);
    }

    public void saveMessage(ChatSession session, String role, String content, Integer order) {
        ChatMessage message = new ChatMessage();
        message.setChatSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setMessageOrder(order);
        
        chatMessageRepository.save(message);
        
        // Update session's updated_at timestamp
        session.setUpdatedAt(java.time.LocalDateTime.now());
        chatSessionRepository.save(session);
    }

    public List<ChatMessage> getSessionMessages(ChatSession session) {
        return chatMessageRepository.findByChatSessionOrderByMessageOrderAsc(session);
    }

    @Transactional
    public void deleteSession(String sessionId, User user) {
        chatSessionRepository.findBySessionIdAndUser(sessionId, user).ifPresent(session -> {
            session.setIsActive(false);
            chatSessionRepository.save(session);
        });
    }

    public boolean isSessionOwnedByUser(String sessionId, User user) {
        return chatSessionRepository.findBySessionIdAndUser(sessionId, user).isPresent();
    }

    public void saveAssistantMessage(String sessionId, String content) {
        Optional<ChatSession> sessionOpt = chatSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            ChatSession session = sessionOpt.get();
            int messageOrder = getSessionMessages(session).size();
            saveMessage(session, "assistant", content, messageOrder);
        }
    }
}