package com.jd.genie.controller;

import com.jd.genie.dto.session.ChatSessionDto;
import com.jd.genie.entity.ChatSession;
import com.jd.genie.entity.User;
import com.jd.genie.service.ChatSessionService;
import com.jd.genie.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SessionController {

    private final ChatSessionService chatSessionService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getUserSessions(Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            List<ChatSession> sessions = chatSessionService.getUserSessions(user);
            List<ChatSessionDto> sessionDtos = sessions.stream()
                    .map(session -> new ChatSessionDto(
                            session.getSessionId(),
                            session.getTitle(),
                            session.getCreatedAt(),
                            session.getUpdatedAt(),
                            session.getMessages().size()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(sessionDtos);
        } catch (Exception e) {
            log.error("Failed to get user sessions", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "获取会话列表失败");
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping
    public ResponseEntity<?> createSession(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            String title = request.get("title");
            ChatSession session = chatSessionService.createSession(user, title);

            ChatSessionDto sessionDto = new ChatSessionDto(
                    session.getSessionId(),
                    session.getTitle(),
                    session.getCreatedAt(),
                    session.getUpdatedAt(),
                    0
            );

            return ResponseEntity.ok(sessionDto);
        } catch (Exception e) {
            log.error("Failed to create session", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "创建会话失败");
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            // 检查会话是否属于当前用户
            if (!chatSessionService.isSessionOwnedByUser(sessionId, user)) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "无权限删除此会话");
                return ResponseEntity.status(403).body(error);
            }

            chatSessionService.deleteSession(sessionId, user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "会话删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete session", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "删除会话失败");
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<?> getSessionMessages(@PathVariable String sessionId, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            ChatSession session = chatSessionService.getSessionByIdAndUser(sessionId, user)
                    .orElseThrow(() -> new RuntimeException("会话不存在或无权限访问"));

            return ResponseEntity.ok(chatSessionService.getSessionMessages(session));
        } catch (Exception e) {
            log.error("Failed to get session messages", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "获取会话消息失败");
            return ResponseEntity.badRequest().body(error);
        }
    }
}