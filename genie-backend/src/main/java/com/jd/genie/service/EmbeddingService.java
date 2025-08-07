package com.jd.genie.service;

import com.jd.genie.config.VectorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    
    private final VectorConfig vectorConfig;
    private final RestTemplate restTemplate = createRestTemplateWithoutProxy();
    
    /**
     * 将文本转换为向量
     * @param text 要向量化的文本
     * @return 向量数组
     */
    public List<Double> generateEmbedding(String text) {
        try {
            VectorConfig.Embedding config = vectorConfig.getEmbedding();
            
            switch (config.getProvider().toLowerCase()) {
                case "openai":
                    return generateOpenAIEmbedding(text, config);
                case "huggingface":
                    return generateHuggingFaceEmbedding(text, config);
                case "ollama":
                    return generateOllamaEmbedding(text, config);
                case "local":
                    return generateLocalEmbedding(text, config);
                default:
                    throw new RuntimeException("Unsupported embedding provider: " + config.getProvider());
            }
        } catch (Exception e) {
            log.error("Failed to generate embedding for text: {}", text.substring(0, Math.min(100, text.length())), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }
    
    /**
     * 批量生成向量
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        return texts.stream()
                .map(this::generateEmbedding)
                .toList();
    }
    
    private List<Double> generateOpenAIEmbedding(String text, VectorConfig.Embedding config) {
        String url = (config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com") + "/v1/embeddings";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", text);
        requestBody.put("model", config.getModel());
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (!data.isEmpty()) {
                    return (List<Double>) data.get(0).get("embedding");
                }
            }
            
            throw new RuntimeException("Failed to get embedding from OpenAI API");
            
        } catch (Exception e) {
            log.error("OpenAI embedding API error", e);
            throw new RuntimeException("OpenAI embedding failed", e);
        }
    }
    
    private List<Double> generateHuggingFaceEmbedding(String text, VectorConfig.Embedding config) {
        // HuggingFace embedding implementation
        String url = config.getBaseUrl() + "/embeddings";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getApiKey() != null) {
            headers.setBearerAuth(config.getApiKey());
        }
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", text);
        requestBody.put("options", Map.of("wait_for_model", true));
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<List> response = restTemplate.postForEntity(url, request, List.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            
            throw new RuntimeException("Failed to get embedding from HuggingFace API");
            
        } catch (Exception e) {
            log.error("HuggingFace embedding API error", e);
            throw new RuntimeException("HuggingFace embedding failed", e);
        }
    }
    
    private List<Double> generateOllamaEmbedding(String text, VectorConfig.Embedding config) {
        // Ollama embedding implementation
        String url = (config.getBaseUrl() != null ? config.getBaseUrl() : "http://127.0.0.1:11434") + "/api/embeddings";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel() != null ? config.getModel() : "nomic-embed-text");
        requestBody.put("prompt", text);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Double> embedding = (List<Double>) body.get("embedding");
                if (embedding != null && !embedding.isEmpty()) {
                    return embedding;
                }
            }
            
            throw new RuntimeException("Failed to get embedding from Ollama API");
            
        } catch (Exception e) {
            log.error("Ollama embedding API error", e);
            throw new RuntimeException("Ollama embedding failed", e);
        }
    }
    
    private List<Double> generateLocalEmbedding(String text, VectorConfig.Embedding config) {
        // Local embedding service implementation
        String url = config.getBaseUrl() + "/v1/embeddings";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", text);
        requestBody.put("model", config.getModel());
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (!data.isEmpty()) {
                    return (List<Double>) data.get(0).get("embedding");
                }
            }
            
            throw new RuntimeException("Failed to get embedding from local service");
            
        } catch (Exception e) {
            log.error("Local embedding service error", e);
            throw new RuntimeException("Local embedding failed", e);
        }
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