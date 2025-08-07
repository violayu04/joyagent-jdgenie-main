package com.jd.genie.controller;

import com.jd.genie.dto.knowledgebase.*;
import com.jd.genie.entity.*;
import com.jd.genie.service.KnowledgeBaseService;
import com.jd.genie.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request,
                                               Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            KnowledgeBase knowledgeBase = knowledgeBaseService.createKnowledgeBase(
                    user, request.getName(), request.getDescription());

            KnowledgeBaseDto dto = convertToDto(knowledgeBase);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Failed to create knowledge base", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "创建知识库失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public ResponseEntity<?> getUserKnowledgeBases(Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            List<KnowledgeBase> knowledgeBases = knowledgeBaseService.getUserKnowledgeBases(user);
            List<KnowledgeBaseDto> dtos = knowledgeBases.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Failed to get user knowledge bases", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "获取知识库列表失败");
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getKnowledgeBase(@PathVariable Long id, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBase(id, user)
                    .orElseThrow(() -> new RuntimeException("知识库不存在或无权限访问"));

            KnowledgeBaseDto dto = convertToDto(knowledgeBase);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Failed to get knowledge base", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "获取知识库失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<?> uploadDocument(@PathVariable Long id,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "description", required = false) String description,
                                          Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            if (file.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "文件不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            Document document = knowledgeBaseService.uploadDocument(id, user, file, description);
            DocumentDto dto = convertToDto(document);
            
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Failed to upload document", e);
            Map<String, Object> error = new HashMap<>();
            error.put("message", "文档上传失败: " + e.getMessage());
            error.put("error", e.getClass().getSimpleName());
            error.put("stackTrace", e.toString());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<?> getKnowledgeBaseDocuments(@PathVariable Long id, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            List<Document> documents = knowledgeBaseService.getDocuments(id, user);
            List<DocumentDto> dtos = documents.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Failed to get knowledge base documents", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "获取文档列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{knowledgeBaseId}/documents/{documentId}/chunks")
    public ResponseEntity<?> getDocumentChunks(@PathVariable Long knowledgeBaseId,
                                             @PathVariable String documentId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String search,
                                             Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
            
            // 验证知识库权限
            knowledgeBaseService.getKnowledgeBase(knowledgeBaseId, user)
                    .orElseThrow(() -> new RuntimeException("知识库不存在或无权限访问"));
            
            List<DocumentChunk> allChunks = knowledgeBaseService.getDocumentChunksByDocumentId(documentId, user);
            
            // 搜索过滤
            List<DocumentChunk> filteredChunks = allChunks;
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                filteredChunks = allChunks.stream()
                        .filter(chunk -> chunk.getContent().toLowerCase().contains(searchLower))
                        .collect(Collectors.toList());
            }
            
            // 分页
            int start = page * size;
            int end = Math.min(start + size, filteredChunks.size());
            List<DocumentChunk> pageChunks = start < filteredChunks.size() ? 
                    filteredChunks.subList(start, end) : Collections.emptyList();
            
            // 转换为DTO
            List<Map<String, Object>> chunkDtos = pageChunks.stream()
                    .map(chunk -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("id", chunk.getId());
                        dto.put("chunkId", chunk.getChunkId());
                        dto.put("chunkIndex", chunk.getChunkIndex());
                        dto.put("content", chunk.getContent());
                        dto.put("tokenCount", chunk.getTokenCount());
                        dto.put("startPos", chunk.getStartPos());
                        dto.put("endPos", chunk.getEndPos());
                        dto.put("createdAt", chunk.getCreatedAt());
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("chunks", chunkDtos);
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", filteredChunks.size());
            response.put("totalPages", (int) Math.ceil((double) filteredChunks.size() / size));
            response.put("hasNext", end < filteredChunks.size());
            response.put("hasPrevious", page > 0);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get document chunks", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "获取文档分块失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/search")
    public ResponseEntity<?> searchInKnowledgeBase(@PathVariable Long id,
                                                 @RequestBody SearchRequest request,
                                                 Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            List<KnowledgeBaseService.SearchResult> results = knowledgeBaseService.searchInKnowledgeBase(
                    id, user, request.getQuery(), request.getTopK() != null ? request.getTopK() : 10);

            List<SearchResultDto> dtos = results.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Failed to search in knowledge base", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "搜索失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKnowledgeBase(@PathVariable Long id, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            knowledgeBaseService.deleteKnowledgeBase(id, user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "知识库删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete knowledge base", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "删除知识库失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable String documentId, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            knowledgeBaseService.deleteDocument(documentId, user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "文档删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete document", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "删除文档失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/documents/{documentId}")
    public ResponseEntity<?> updateDocument(@PathVariable String documentId, 
                                          @RequestBody Map<String, String> updateRequest,
                                          Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            String newFilename = updateRequest.get("filename");
            if (newFilename == null || newFilename.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "文件名不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            DocumentDto updatedDoc = knowledgeBaseService.updateDocumentFilename(documentId, newFilename.trim(), user);

            return ResponseEntity.ok(updatedDoc);
        } catch (Exception e) {
            log.error("Failed to update document", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "更新文档失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateKnowledgeBase(@PathVariable Long id,
                                               @RequestBody Map<String, String> updateRequest,
                                               Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();

            String newName = updateRequest.get("name");
            String newDescription = updateRequest.get("description");
            
            if (newName != null && newName.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "知识库名称不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            KnowledgeBaseDto updatedKB = knowledgeBaseService.updateKnowledgeBase(id, newName, newDescription, user);

            return ResponseEntity.ok(updatedKB);
        } catch (Exception e) {
            log.error("Failed to update knowledge base", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "更新知识库失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // DTO转换方法
    private KnowledgeBaseDto convertToDto(KnowledgeBase knowledgeBase) {
        KnowledgeBaseDto dto = new KnowledgeBaseDto();
        dto.setId(knowledgeBase.getId());
        dto.setName(knowledgeBase.getName());
        dto.setDescription(knowledgeBase.getDescription());
        dto.setDocumentCount(knowledgeBase.getDocumentCount());
        dto.setCreatedAt(knowledgeBase.getCreatedAt());
        dto.setUpdatedAt(knowledgeBase.getUpdatedAt());
        return dto;
    }

    private DocumentDto convertToDto(Document document) {
        DocumentDto dto = new DocumentDto();
        dto.setDocumentId(document.getDocumentId());
        dto.setFilename(document.getOriginalFilename());
        dto.setContentType(document.getContentType());
        dto.setFileSize(document.getFileSize());
        dto.setStatus(document.getStatus().name());
        dto.setErrorMessage(document.getErrorMessage());
        dto.setMetadata(document.getMetadata());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        return dto;
    }

    private SearchResultDto convertToDto(KnowledgeBaseService.SearchResult result) {
        SearchResultDto dto = new SearchResultDto();
        dto.setChunkId(result.getChunkId());
        dto.setContent(result.getContent());
        dto.setScore(result.getScore());
        dto.setChunkIndex(result.getChunkIndex());
        dto.setTokenCount(result.getTokenCount());
        
        if (result.getDocument() != null) {
            dto.setDocumentId(result.getDocument().getDocumentId());
            dto.setFilename(result.getDocument().getOriginalFilename());
        }
        
        return dto;
    }
}