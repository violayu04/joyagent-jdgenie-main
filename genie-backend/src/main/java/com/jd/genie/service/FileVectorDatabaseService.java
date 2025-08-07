package com.jd.genie.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileVectorDatabaseService {
    
    private final String VECTOR_DATA_DIR = "./vector_data";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public FileVectorDatabaseService() {
        // Create vector data directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(VECTOR_DATA_DIR));
        } catch (IOException e) {
            log.error("Failed to create vector data directory", e);
        }
    }
    
    /**
     * Add document to vector database
     */
    public boolean addDocument(String chunkId, String content, List<Double> embedding, Map<String, Object> metadata) {
        try {
            VectorDocument doc = new VectorDocument();
            doc.setId(chunkId);
            doc.setContent(content);
            doc.setEmbedding(embedding);
            doc.setMetadata(metadata);
            doc.setTimestamp(System.currentTimeMillis());
            
            String filePath = VECTOR_DATA_DIR + "/" + chunkId + ".json";
            objectMapper.writeValue(new File(filePath), doc);
            
            log.info("Successfully saved document {} to file-based vector store", chunkId);
            return true;
        } catch (Exception e) {
            log.error("Failed to save document {} to vector store", chunkId, e);
            return false;
        }
    }
    
    /**
     * Batch add documents
     */
    public int addDocuments(List<VectorDocument> documents) {
        int successCount = 0;
        for (VectorDocument doc : documents) {
            if (addDocument(doc.getId(), doc.getContent(), doc.getEmbedding(), doc.getMetadata())) {
                successCount++;
            }
        }
        return successCount;
    }
    
    /**
     * Search similar documents using cosine similarity
     */
    public List<SearchResult> search(List<Double> queryEmbedding, int topK, Long knowledgeBaseId) {
        try {
            List<SearchResult> results = new ArrayList<>();
            
            File dataDir = new File(VECTOR_DATA_DIR);
            File[] vectorFiles = dataDir.listFiles((dir, name) -> name.endsWith(".json"));
            
            if (vectorFiles == null) {
                return results;
            }
            
            for (File file : vectorFiles) {
                try {
                    VectorDocument doc = objectMapper.readValue(file, VectorDocument.class);
                    
                    // Filter by knowledge base if specified
                    if (knowledgeBaseId != null) {
                        Object kbId = doc.getMetadata().get("knowledge_base_id");
                        if (!knowledgeBaseId.equals(kbId)) {
                            continue;
                        }
                    }
                    
                    // Calculate cosine similarity
                    double similarity = calculateCosineSimilarity(queryEmbedding, doc.getEmbedding());
                    
                    SearchResult result = new SearchResult();
                    result.setId(doc.getId());
                    result.setContent(doc.getContent());
                    result.setScore(similarity);
                    result.setMetadata(doc.getMetadata());
                    
                    results.add(result);
                } catch (Exception e) {
                    log.warn("Failed to process vector file {}", file.getName(), e);
                }
            }
            
            // Sort by similarity score and return top K
            return results.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to search in vector database", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Delete document
     */
    public boolean deleteDocument(String chunkId) {
        try {
            String filePath = VECTOR_DATA_DIR + "/" + chunkId + ".json";
            File file = new File(filePath);
            if (file.exists()) {
                return file.delete();
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to delete document {}", chunkId, e);
            return false;
        }
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1.size() != vec2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vec1.size(); i++) {
            double a = vec1.get(i);
            double b = vec2.get(i);
            
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    // Data classes
    public static class VectorDocument {
        private String id;
        private String content;
        private List<Double> embedding;
        private Map<String, Object> metadata;
        private long timestamp;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public List<Double> getEmbedding() { return embedding; }
        public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    public static class SearchResult {
        private String id;
        private String content;
        private double score;
        private Map<String, Object> metadata;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}