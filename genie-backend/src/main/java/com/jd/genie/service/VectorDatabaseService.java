package com.jd.genie.service;

import com.jd.genie.config.VectorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorDatabaseService {
    
    private final VectorConfig vectorConfig;
    private final RestTemplate restTemplate = createRestTemplateWithoutProxy();
    private final FileVectorDatabaseService fileVectorService;
    
    /**
     * 向向量数据库添加文档块
     * @param chunkId 文档块ID
     * @param content 文本内容
     * @param embedding 向量
     * @param metadata 元数据
     * @return 存储是否成功
     */
    public boolean addDocument(String chunkId, String content, List<Double> embedding, Map<String, Object> metadata) {
        try {
            VectorConfig.Database config = vectorConfig.getDatabase();
            
            switch (config.getType().toLowerCase()) {
                case "file":
                    return fileVectorService.addDocument(chunkId, content, embedding, metadata);
                case "chroma":
                    return addToChroma(chunkId, content, embedding, metadata, config);
                case "qdrant":
                    return addToQdrant(chunkId, content, embedding, metadata, config);
                case "milvus":
                    return addToMilvus(chunkId, content, embedding, metadata, config);
                default:
                    throw new RuntimeException("Unsupported vector database: " + config.getType());
            }
        } catch (Exception e) {
            log.error("Failed to add document to vector database: chunkId={}", chunkId, e);
            return false;
        }
    }
    
    /**
     * 批量添加文档块
     * @param documents 文档块列表
     * @return 添加成功的数量
     */
    public int addDocuments(List<VectorDocument> documents) {
        VectorConfig.Database config = vectorConfig.getDatabase();
        
        if ("file".equals(config.getType().toLowerCase())) {
            // Use file-based batch add for better performance
            List<FileVectorDatabaseService.VectorDocument> fileDocuments = new ArrayList<>();
            for (VectorDocument doc : documents) {
                FileVectorDatabaseService.VectorDocument fileDoc = new FileVectorDatabaseService.VectorDocument();
                fileDoc.setId(doc.getId());
                fileDoc.setContent(doc.getContent());
                fileDoc.setEmbedding(doc.getEmbedding());
                fileDoc.setMetadata(doc.getMetadata());
                fileDocuments.add(fileDoc);
            }
            return fileVectorService.addDocuments(fileDocuments);
        } else {
            // Fall back to individual adds for other database types
            int successCount = 0;
            for (VectorDocument doc : documents) {
                if (addDocument(doc.getId(), doc.getContent(), doc.getEmbedding(), doc.getMetadata())) {
                    successCount++;
                }
            }
            return successCount;
        }
    }
    
    /**
     * 相似性搜索
     * @param queryEmbedding 查询向量
     * @param topK 返回结果数量
     * @param knowledgeBaseId 知识库ID（可选）
     * @return 搜索结果
     */
    public List<SearchResult> search(List<Double> queryEmbedding, int topK, Long knowledgeBaseId) {
        try {
            VectorConfig.Database config = vectorConfig.getDatabase();
            
            switch (config.getType().toLowerCase()) {
                case "file":
                    return convertToSearchResults(fileVectorService.search(queryEmbedding, topK, knowledgeBaseId));
                case "chroma":
                    return searchInChroma(queryEmbedding, topK, knowledgeBaseId, config);
                case "qdrant":
                    return searchInQdrant(queryEmbedding, topK, knowledgeBaseId, config);
                case "milvus":
                    return searchInMilvus(queryEmbedding, topK, knowledgeBaseId, config);
                default:
                    throw new RuntimeException("Unsupported vector database: " + config.getType());
            }
        } catch (Exception e) {
            log.error("Failed to search in vector database", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 删除文档块
     * @param chunkId 文档块ID
     * @return 删除是否成功
     */
    public boolean deleteDocument(String chunkId) {
        try {
            VectorConfig.Database config = vectorConfig.getDatabase();
            
            switch (config.getType().toLowerCase()) {
                case "file":
                    return fileVectorService.deleteDocument(chunkId);
                case "chroma":
                    return deleteFromChroma(chunkId, config);
                case "qdrant":
                    return deleteFromQdrant(chunkId, config);
                case "milvus":
                    return deleteFromMilvus(chunkId, config);
                default:
                    throw new RuntimeException("Unsupported vector database: " + config.getType());
            }
        } catch (Exception e) {
            log.error("Failed to delete document from vector database: chunkId={}", chunkId, e);
            return false;
        }
    }
    
    // Chroma implementation
    private boolean addToChroma(String chunkId, String content, List<Double> embedding, 
                               Map<String, Object> metadata, VectorConfig.Database config) {
        createCollectionIfNotExists(config); 

        String url = String.format("http://127.0.0.1:%d/api/v2/collections/%s/upsert", 
                config.getPort(), config.getCollection());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ids", Collections.singletonList(chunkId));
        requestBody.put("documents", Collections.singletonList(content));
        requestBody.put("embeddings", Collections.singletonList(embedding));
        requestBody.put("metadatas", Collections.singletonList(metadata));
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Chroma add document error", e);
            return false;
        }
    }
    
    private List<SearchResult> searchInChroma(List<Double> queryEmbedding, int topK, 
                                            Long knowledgeBaseId, VectorConfig.Database config) {
        createCollectionIfNotExists(config); 
        String url = String.format("http://127.0.0.1:%d/api/v2/collections/%s/query", 
                config.getPort(), config.getCollection());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query_embeddings", Collections.singletonList(queryEmbedding));
        requestBody.put("n_results", topK);
        
        if (knowledgeBaseId != null) {
            Map<String, Object> where = new HashMap<>();
            where.put("knowledge_base_id", knowledgeBaseId);
            requestBody.put("where", where);
        }
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseChromaSearchResults(response.getBody());
            }
            
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Chroma search error", e);
            return Collections.emptyList();
        }
    }
    
    private boolean deleteFromChroma(String chunkId, VectorConfig.Database config) {
        createCollectionIfNotExists(config); 
        String url = String.format("http://127.0.0.1:%d/api/v2/collections/%s/delete", 
                config.getPort(), config.getCollection());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ids", Collections.singletonList(chunkId));
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Chroma delete document error", e);
            return false;
        }
    }
    
    // Placeholder implementations for other databases
    private boolean addToQdrant(String chunkId, String content, List<Double> embedding, 
                               Map<String, Object> metadata, VectorConfig.Database config) {
        // Qdrant implementation
        log.info("Qdrant implementation not yet available");
        return false;
    }
    
    private List<SearchResult> searchInQdrant(List<Double> queryEmbedding, int topK, 
                                            Long knowledgeBaseId, VectorConfig.Database config) {
        // Qdrant implementation
        log.info("Qdrant implementation not yet available");
        return Collections.emptyList();
    }
    
    private boolean deleteFromQdrant(String chunkId, VectorConfig.Database config) {
        // Qdrant implementation
        log.info("Qdrant implementation not yet available");
        return false;
    }
    
    private boolean addToMilvus(String chunkId, String content, List<Double> embedding, 
                               Map<String, Object> metadata, VectorConfig.Database config) {
        // Milvus implementation
        log.info("Milvus implementation not yet available");
        return false;
    }
    
    private List<SearchResult> searchInMilvus(List<Double> queryEmbedding, int topK, 
                                            Long knowledgeBaseId, VectorConfig.Database config) {
        // Milvus implementation
        log.info("Milvus implementation not yet available");
        return Collections.emptyList();
    }
    
    private boolean deleteFromMilvus(String chunkId, VectorConfig.Database config) {
        // Milvus implementation
        log.info("Milvus implementation not yet available");
        return false;
    }
    
    private List<SearchResult> parseChromaSearchResults(Map<String, Object> responseBody) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            List<List<String>> ids = (List<List<String>>) responseBody.get("ids");
            List<List<String>> documents = (List<List<String>>) responseBody.get("documents");
            List<List<Double>> distances = (List<List<Double>>) responseBody.get("distances");
            List<List<Map<String, Object>>> metadatas = (List<List<Map<String, Object>>>) responseBody.get("metadatas");
            
            if (ids != null && !ids.isEmpty()) {
                List<String> resultIds = ids.get(0);
                List<String> resultDocs = documents != null && !documents.isEmpty() ? documents.get(0) : Collections.emptyList();
                List<Double> resultDistances = distances != null && !distances.isEmpty() ? distances.get(0) : Collections.emptyList();
                List<Map<String, Object>> resultMetadatas = metadatas != null && !metadatas.isEmpty() ? metadatas.get(0) : Collections.emptyList();
                
                for (int i = 0; i < resultIds.size(); i++) {
                    SearchResult result = new SearchResult();
                    result.setId(resultIds.get(i));
                    result.setContent(i < resultDocs.size() ? resultDocs.get(i) : "");
                    result.setScore(i < resultDistances.size() ? 1.0 - resultDistances.get(i) : 0.0); // Convert distance to similarity
                    result.setMetadata(i < resultMetadatas.size() ? resultMetadatas.get(i) : Collections.emptyMap());
                    
                    results.add(result);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Chroma search results", e);
        }
        
        return results;
    }
    
    // Data classes
    public static class VectorDocument {
        private String id;
        private String content;
        private List<Double> embedding;
        private Map<String, Object> metadata;
        
        public VectorDocument(String id, String content, List<Double> embedding, Map<String, Object> metadata) {
            this.id = id;
            this.content = content;
            this.embedding = embedding;
            this.metadata = metadata;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public List<Double> getEmbedding() { return embedding; }
        public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
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
    
    private void createCollectionIfNotExists(VectorConfig.Database config) {
    String collectionName = config.getCollection();
    // STEP 1: Use the CORRECT /v2 path to check if the collection exists
    String getUrl = String.format("http://127.0.0.1:%d/api/v2/collections/%s", 
                                  config.getPort(), collectionName);
    
    RestTemplate restTemplate = createRestTemplateWithoutProxy();

    try {
        log.info("Checking if collection '{}' exists at {}", collectionName, getUrl);
        restTemplate.getForEntity(getUrl, String.class);
        log.info("Collection '{}' already exists.", collectionName);
        return; // Success, collection exists.
    } catch (HttpClientErrorException.NotFound e) {
        // A 404 is GOOD here. It means the collection does not exist and we must create it.
        log.info("Collection '{}' not found, proceeding to create it.", collectionName);
    } catch (Exception e) {
        log.error("An unexpected error occurred while checking for collection '{}'", collectionName, e);
        throw new RuntimeException("Could not verify collection existence", e);
    }

    // STEP 2: If we get here, create the collection using the /v2 path and the required metadata.
    String createUrl = String.format("http://127.0.0.1:%d/api/v2/collections", 
                                     config.getPort());
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("name", collectionName);
    
    // THIS IS THE CRITICAL NEW PIECE: Required metadata for the collection
    Map<String, String> metadata = new HashMap<>();
    metadata.put("hnsw:space", "l2"); // "l2" (Euclidean distance) is a standard for many models
    requestBody.put("metadata", metadata);

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

    try {
        log.info("Creating collection '{}' with POST to {}", collectionName, createUrl);
        ResponseEntity<String> response = restTemplate.postForEntity(createUrl, request, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
             log.info("Successfully created collection '{}'", collectionName);
        } else {
             throw new RuntimeException("Failed to create collection, status: " + response.getStatusCode());
        }
    } catch (Exception e) {
        log.error("Failed to create collection '{}'", collectionName, e);
        throw new RuntimeException("Could not create collection", e);
    }
    }

    /**
     * Convert file-based search results to standard SearchResult format
     */
    private List<SearchResult> convertToSearchResults(List<FileVectorDatabaseService.SearchResult> fileResults) {
        List<SearchResult> results = new ArrayList<>();
        for (FileVectorDatabaseService.SearchResult fileResult : fileResults) {
            SearchResult result = new SearchResult();
            result.setId(fileResult.getId());
            result.setContent(fileResult.getContent());
            result.setScore(fileResult.getScore());
            result.setMetadata(fileResult.getMetadata());
            results.add(result);
        }
        return results;
    }
    
    private static RestTemplate createRestTemplateWithoutProxy() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Configure HTTP client factory to bypass proxy completely
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        
        // Set additional timeout and connection settings
        factory.setConnectTimeout(5000);  // 5 second connection timeout
        factory.setReadTimeout(30000);    // 30 second read timeout
        
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }
}

