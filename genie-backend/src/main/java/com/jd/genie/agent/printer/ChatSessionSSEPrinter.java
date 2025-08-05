package com.jd.genie.agent.printer;

import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class ChatSessionSSEPrinter extends SSEPrinter {
    
    private final ChatSessionService chatSessionService;
    private final StringBuilder responseContent;
    private final String sessionId;
    
    public ChatSessionSSEPrinter(SseEmitter emitter, AgentRequest request, Integer agentType, ChatSessionService chatSessionService) {
        super(emitter, request, agentType);
        this.chatSessionService = chatSessionService;
        this.responseContent = new StringBuilder();
        this.sessionId = request.getSessionId();
    }
    
    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        // 收集响应内容
        if ("result".equals(messageType) && message instanceof String) {
            responseContent.append((String) message);
        } else if ("agent_stream".equals(messageType) && message instanceof String) {
            responseContent.append((String) message);
        }
        
        // 调用父类方法发送SSE
        super.send(messageId, messageType, message, digitalEmployee, isFinal);
        
        // 如果是最终消息，保存完整的助手响应
        if (Boolean.TRUE.equals(isFinal) && "result".equals(messageType)) {
            saveAssistantResponse();
        }
    }
    
    @Override
    public void close() {
        // 在关闭连接前保存响应（容错）
        if (responseContent.length() > 0) {
            saveAssistantResponse();
        }
        super.close();
    }
    
    private void saveAssistantResponse() {
        try {
            if (responseContent.length() > 0 && sessionId != null) {
                String finalResponse = responseContent.toString().trim();
                if (!finalResponse.isEmpty()) {
                    chatSessionService.saveAssistantMessage(sessionId, finalResponse);
                    log.info("Assistant response saved to session: {}", sessionId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to save assistant response for session: {}", sessionId, e);
        }
    }
}