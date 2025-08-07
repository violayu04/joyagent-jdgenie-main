package com.jd.genie.service;

import com.jd.genie.entity.*;
import com.jd.genie.repository.*;
import com.jd.genie.service.TextChunkingService.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {
    
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final UserService userService;
    private final EmbeddingService embeddingService;
    private final VectorDatabaseService vectorDatabaseService;
    private final TextChunkingService textChunkingService;
    
    /**
     * 创建知识库
     */
    @Transactional
    public KnowledgeBase createKnowledgeBase(User user, String name, String description) {
        // 检查名称是否已存在
        if (knowledgeBaseRepository.existsByUserAndNameAndIsActive(user, name, true)) {
            throw new RuntimeException("知识库名称已存在");
        }
        
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(name);
        knowledgeBase.setDescription(description);
        knowledgeBase.setUser(user);
        knowledgeBase.setDocumentCount(0);
        knowledgeBase.setIsActive(true);
        
        return knowledgeBaseRepository.save(knowledgeBase);
    }
    
    /**
     * 获取用户的知识库列表
     */
    public List<KnowledgeBase> getUserKnowledgeBases(User user) {
        return knowledgeBaseRepository.findByUserAndIsActiveTrueOrderByUpdatedAtDesc(user);
    }
    
    /**
     * 获取指定知识库
     */
    public Optional<KnowledgeBase> getKnowledgeBase(Long id, User user) {
        return knowledgeBaseRepository.findByIdAndUser(id, user);
    }
    
    /**
     * 上传文档到知识库
     */
    @Transactional
    public Document uploadDocument(Long knowledgeBaseId, User user, MultipartFile file, String description) {
        // 验证知识库权限
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findByIdAndUser(knowledgeBaseId, user)
                .orElseThrow(() -> new RuntimeException("知识库不存在或无权限访问"));
        
        log.info("Starting document upload: knowledgeBaseId={}, filename={}, size={}", 
                knowledgeBaseId, file.getOriginalFilename(), file.getSize());
        
        try {
            // 计算文件内容哈希
            byte[] fileBytes = file.getBytes();
            String contentHash = calculateMD5Hash(fileBytes);
            
            // 检查是否已存在相同内容的文档
            Optional<Document> existingDoc = documentRepository.findByKnowledgeBaseAndContentHash(knowledgeBase, contentHash);
            if (existingDoc.isPresent()) {
                Document existing = existingDoc.get();
                // 如果文档已成功处理，则不允许重复上传
                if (existing.getStatus() == Document.DocumentStatus.COMPLETED) {
                    throw new RuntimeException("相同内容的文档已存在");
                }
                // 如果文档处理失败或还在处理中，删除旧记录，允许重新上传
                log.info("Found existing document with status {}, removing and re-uploading: {}", 
                        existing.getStatus(), existing.getDocumentId());
                documentRepository.delete(existing);
            }
            
            // 保存文件到本地
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = saveFileToLocal(fileBytes, filename);
            
            // 创建文档记录
            Document document = new Document();
            document.setDocumentId(UUID.randomUUID().toString());
            document.setKnowledgeBase(knowledgeBase);
            document.setFilename(filename);
            document.setOriginalFilename(file.getOriginalFilename());
            document.setFilePath(filePath.toString());
            document.setContentType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setContentHash(contentHash);
            document.setStatus(Document.DocumentStatus.PROCESSING);
            
            // 设置元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("description", description);
            metadata.put("upload_time", LocalDateTime.now().toString());
            document.setMetadata(metadata);
            
            document = documentRepository.save(document);
            
            // 异步处理文档向量化
            processDocumentAsync(document);
            
            return document;
            
        } catch (IOException e) {
            log.error("Failed to upload document", e);
            throw new RuntimeException("文档上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 异步处理文档向量化
     */
    private void processDocumentAsync(Document document) {
        CompletableFuture.runAsync(() -> {
            try {
                processDocumentVectorization(document);
            } catch (Exception e) {
                log.error("Failed to process document vectorization for document: {}", document.getDocumentId(), e);
                updateDocumentStatus(document, Document.DocumentStatus.FAILED, e.getMessage());
            }
        });
    }
    
    /**
     * 处理文档向量化
     */
    @Transactional
    public void processDocumentVectorization(Document document) throws IOException {
        try {
            log.info("Starting vectorization for document: {}", document.getDocumentId());
            
            // 1. 提取文档内容（调用现有的文档分析服务）
            String content = extractDocumentContent(document);
            
            // 2. 文本分块
            List<TextChunk> chunks = textChunkingService.chunkText(content);
            log.info("Document {} split into {} chunks", document.getDocumentId(), chunks.size());
            
            // 3. 生成向量并存储
            List<DocumentChunk> documentChunks = new ArrayList<>();
            List<VectorDatabaseService.VectorDocument> vectorDocuments = new ArrayList<>();
            
            for (TextChunk chunk : chunks) {
                // 生成向量
                List<Double> embedding = embeddingService.generateEmbedding(chunk.getContent());
                
                // 创建文档块记录
                DocumentChunk documentChunk = new DocumentChunk();
                documentChunk.setChunkId(UUID.randomUUID().toString());
                documentChunk.setDocument(document);
                documentChunk.setContent(chunk.getContent());
                documentChunk.setChunkIndex(chunk.getChunkIndex());
                documentChunk.setStartPos(chunk.getStartPos());
                documentChunk.setEndPos(chunk.getEndPos());
                documentChunk.setTokenCount(chunk.getTokenCount());
                documentChunk.setEmbeddingId(documentChunk.getChunkId()); // 使用chunkId作为embeddingId
                
                // 设置元数据
                Map<String, Object> chunkMetadata = new HashMap<>();
                chunkMetadata.put("document_id", document.getDocumentId());
                chunkMetadata.put("knowledge_base_id", document.getKnowledgeBase().getId());
                chunkMetadata.put("filename", document.getOriginalFilename());
                chunkMetadata.put("chunk_index", chunk.getChunkIndex());
                chunkMetadata.put("token_count", chunk.getTokenCount());
                documentChunk.setMetadata(chunkMetadata);
                
                documentChunks.add(documentChunk);
                
                // 准备向量数据库文档
                VectorDatabaseService.VectorDocument vectorDoc = new VectorDatabaseService.VectorDocument(
                        documentChunk.getChunkId(),
                        chunk.getContent(),
                        embedding,
                        chunkMetadata
                );
                vectorDocuments.add(vectorDoc);
            }
            
            // 4. 保存到数据库
            documentChunkRepository.saveAll(documentChunks);
            
            // 5. 保存到向量数据库
            int successCount = vectorDatabaseService.addDocuments(vectorDocuments);
            log.info("Successfully added {} out of {} chunks to vector database for document: {}", 
                    successCount, vectorDocuments.size(), document.getDocumentId());
            
            if (successCount != vectorDocuments.size()) {
                throw new RuntimeException("部分向量化失败: " + successCount + "/" + vectorDocuments.size());
            }
            
            // 6. 更新文档状态
            updateDocumentStatus(document, Document.DocumentStatus.COMPLETED, null);
            
            // 7. 更新知识库文档数量
            updateKnowledgeBaseDocumentCount(document.getKnowledgeBase());
            
            log.info("Document vectorization completed successfully for: {}", document.getDocumentId());
            
        } catch (Exception e) {
            log.error("Failed to vectorize document: {}", document.getDocumentId(), e);
            updateDocumentStatus(document, Document.DocumentStatus.FAILED, e.getMessage());
            throw e;
        }
    }
    
    /**
     * 在知识库中搜索
     */
    public List<SearchResult> searchInKnowledgeBase(Long knowledgeBaseId, User user, String query, int topK) {
        // 验证知识库权限
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findByIdAndUser(knowledgeBaseId, user)
                .orElseThrow(() -> new RuntimeException("知识库不存在或无权限访问"));
        
        try {
            // 1. 将查询向量化
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);
            
            // 2. 在向量数据库中搜索
            List<VectorDatabaseService.SearchResult> vectorResults = vectorDatabaseService.search(
                    queryEmbedding, topK, knowledgeBaseId);
            
            // 3. 转换为业务结果
            List<SearchResult> results = new ArrayList<>();
            for (VectorDatabaseService.SearchResult vectorResult : vectorResults) {
                Optional<DocumentChunk> chunk = documentChunkRepository.findByChunkId(vectorResult.getId());
                if (chunk.isPresent()) {
                    DocumentChunk documentChunk = chunk.get();
                    
                    SearchResult result = new SearchResult();
                    result.setChunkId(documentChunk.getChunkId());
                    result.setContent(documentChunk.getContent());
                    result.setScore(vectorResult.getScore());
                    result.setDocument(documentChunk.getDocument());
                    result.setChunkIndex(documentChunk.getChunkIndex());
                    result.setTokenCount(documentChunk.getTokenCount());
                    
                    results.add(result);
                }
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Failed to search in knowledge base: {}", knowledgeBaseId, e);
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取知识库下的所有文档
     */
    public List<Document> getDocuments(Long knowledgeBaseId, User user) {
        // 验证知识库权限
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findByIdAndUser(knowledgeBaseId, user)
                .orElseThrow(() -> new RuntimeException("知识库不存在或无权限访问"));
        
        return documentRepository.findByKnowledgeBaseOrderByCreatedAtDesc(knowledgeBase);
    }
    
    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(String documentId, User user) {
        Document document = documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        
        // 验证权限
        if (!document.getKnowledgeBase().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("无权限删除该文档");
        }
        
        try {
            // 1. 删除向量数据库中的记录
            List<DocumentChunk> chunks = documentChunkRepository.findByDocumentOrderByChunkIndex(document);
            for (DocumentChunk chunk : chunks) {
                vectorDatabaseService.deleteDocument(chunk.getChunkId());
            }
            
            // 2. 删除数据库记录
            documentChunkRepository.deleteByDocument(document);
            documentRepository.delete(document);
            
            // 3. 删除本地文件
            try {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete local file: {}", document.getFilePath(), e);
            }
            
            // 4. 更新知识库文档数量
            updateKnowledgeBaseDocumentCount(document.getKnowledgeBase());
            
            log.info("Document deleted successfully: {}", documentId);
            
        } catch (Exception e) {
            log.error("Failed to delete document: {}", documentId, e);
            throw new RuntimeException("删除文档失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除知识库
     */
    @Transactional
    public void deleteKnowledgeBase(Long knowledgeBaseId, User user) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findByIdAndUser(knowledgeBaseId, user)
                .orElseThrow(() -> new RuntimeException("知识库不存在或无权限访问"));
        
        try {
            // 1. 删除所有文档
            List<Document> documents = documentRepository.findByKnowledgeBaseOrderByCreatedAtDesc(knowledgeBase);
            for (Document document : documents) {
                deleteDocument(document.getDocumentId(), user);
            }
            
            // 2. 软删除知识库
            knowledgeBase.setIsActive(false);
            knowledgeBase.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseRepository.save(knowledgeBase);
            
            log.info("Knowledge base deleted successfully: {}", knowledgeBaseId);
            
        } catch (Exception e) {
            log.error("Failed to delete knowledge base: {}", knowledgeBaseId, e);
            throw new RuntimeException("删除知识库失败: " + e.getMessage());
        }
    }
    
    // 私有辅助方法
    
    private String extractDocumentContent(Document document) throws IOException {
        // 根据文件类型选择不同的内容提取方法
        String contentType = document.getContentType();
        Path filePath = Paths.get(document.getFilePath());
        
        if (contentType == null) {
            contentType = guessContentTypeFromExtension(document.getOriginalFilename());
        }
        
        log.info("Extracting content from file: {} with content type: {}", 
                document.getOriginalFilename(), contentType);
        
        // 目前简化实现，只处理文本文件和简单的文档格式
        if (isTextFile(contentType)) {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } else if (isPdfFile(contentType)) {
            // PDF文件目前返回占位符内容，后续可以集成PDF解析库
            return "PDF文档内容提取功能开发中。文件名: " + document.getOriginalFilename() + 
                   ", 大小: " + document.getFileSize() + " 字节";
        } else {
            // 其他文件类型返回占位符内容
            return "文档类型 " + contentType + " 的内容提取功能开发中。文件名: " + 
                   document.getOriginalFilename() + ", 大小: " + document.getFileSize() + " 字节";
        }
    }
    
    private String guessContentTypeFromExtension(String filename) {
        if (filename == null) return "application/octet-stream";
        
        String lowerCase = filename.toLowerCase();
        if (lowerCase.endsWith(".txt")) return "text/plain";
        if (lowerCase.endsWith(".md")) return "text/markdown";
        if (lowerCase.endsWith(".pdf")) return "application/pdf";
        if (lowerCase.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lowerCase.endsWith(".doc")) return "application/msword";
        if (lowerCase.endsWith(".json")) return "application/json";
        if (lowerCase.endsWith(".csv")) return "text/csv";
        
        return "application/octet-stream";
    }
    
    private boolean isTextFile(String contentType) {
        return contentType != null && (
            contentType.startsWith("text/") ||
            contentType.equals("application/json") ||
            contentType.equals("application/xml")
        );
    }
    
    private boolean isPdfFile(String contentType) {
        return "application/pdf".equals(contentType);
    }
    
    private Path saveFileToLocal(byte[] fileBytes, String filename) throws IOException {
        // 创建上传目录
        Path uploadDir = Paths.get("uploads", "knowledge-base");
        Files.createDirectories(uploadDir);
        
        // 保存文件
        Path filePath = uploadDir.resolve(filename);
        Files.write(filePath, fileBytes);
        
        return filePath;
    }
    
    private String calculateMD5Hash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    private void updateDocumentStatus(Document document, Document.DocumentStatus status, String errorMessage) {
        try {
            document.setStatus(status);
            document.setErrorMessage(errorMessage);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to update document status", e);
        }
    }
    
    private void updateKnowledgeBaseDocumentCount(KnowledgeBase knowledgeBase) {
        try {
            long count = documentRepository.countByKnowledgeBase(knowledgeBase);
            knowledgeBase.setDocumentCount((int) count);
            knowledgeBase.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseRepository.save(knowledgeBase);
        } catch (Exception e) {
            log.error("Failed to update knowledge base document count", e);
        }
    }
    
    /**
     * 搜索结果类
     */
    public static class SearchResult {
        private String chunkId;
        private String content;
        private double score;
        private Document document;
        private Integer chunkIndex;
        private Integer tokenCount;
        
        // Getters and setters
        public String getChunkId() { return chunkId; }
        public void setChunkId(String chunkId) { this.chunkId = chunkId; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        
        public Document getDocument() { return document; }
        public void setDocument(Document document) { this.document = document; }
        
        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public Integer getTokenCount() { return tokenCount; }
        public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    }
}