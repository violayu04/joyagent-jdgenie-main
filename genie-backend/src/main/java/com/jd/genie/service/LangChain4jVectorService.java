package com.jd.genie.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
//@Service  // Temporarily disabled due to proxy issues
public class LangChain4jVectorService {

    @Value("${vector.database.host:localhost}")
    private String chromaHost;
    
    @Value("${vector.database.port:8000}")
    private int chromaPort;
    
    @Value("${vector.database.collection:genie_documents}")
    private String collectionName;
    
    @Value("${vector.embedding.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${vector.embedding.model:nomic-embed-text}")
    private String embeddingModel;

    private EmbeddingModel embeddingModelService;
    private EmbeddingStore<TextSegment> embeddingStore;

    @PostConstruct
    public void initialize() {
        log.info("Initializing LangChain4j services...");
        
        // Initialize Ollama embedding model
        embeddingModelService = OllamaEmbeddingModel.builder()
            .baseUrl(ollamaBaseUrl)
            .modelName(embeddingModel)
            .build();
        
        // Initialize Chroma vector store
        String chromaUrl = String.format("http://%s:%d", chromaHost, chromaPort);
        embeddingStore = ChromaEmbeddingStore.builder()
            .baseUrl(chromaUrl)
            .collectionName(collectionName)
            .build();
        
        log.info("LangChain4j services initialized successfully");
        log.info("- Ollama URL: {}", ollamaBaseUrl);
        log.info("- Chroma URL: {}", chromaUrl);
        log.info("- Collection: {}", collectionName);
    }

    /**
     * Add document chunk to vector store
     */
    public void addDocument(String chunkId, String content) {
        try {
            log.info("Adding document chunk {} to vector store", chunkId);
            
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModelService.embed(textSegment).content();
            
            embeddingStore.add(embedding, textSegment);
            
            log.info("Successfully added document chunk {} to vector store", chunkId);
        } catch (Exception e) {
            log.error("Failed to add document chunk {} to vector store", chunkId, e);
            throw new RuntimeException("Failed to add document to vector store", e);
        }
    }

    /**
     * Search similar documents
     */
    public List<String> searchSimilar(String query, int maxResults) {
        try {
            log.info("Searching for similar documents with query: {}", query);
            
            Embedding queryEmbedding = embeddingModelService.embed(query).content();
            
            var results = embeddingStore.findRelevant(queryEmbedding, maxResults);
            
            List<String> contents = results.stream()
                .map(match -> match.embedded().text())
                .toList();
            
            log.info("Found {} similar documents", contents.size());
            return contents;
            
        } catch (Exception e) {
            log.error("Failed to search similar documents", e);
            throw new RuntimeException("Failed to search vector store", e);
        }
    }

    /**
     * Delete document from vector store
     */
    public void deleteDocument(String chunkId) {
        try {
            log.info("Deleting document chunk {} from vector store", chunkId);
            // Note: LangChain4j ChromaEmbeddingStore doesn't support individual delete by ID
            // This would require a different approach for production use
            log.warn("Individual document deletion not implemented for LangChain4j");
        } catch (Exception e) {
            log.error("Failed to delete document chunk {} from vector store", chunkId, e);
            throw new RuntimeException("Failed to delete document from vector store", e);
        }
    }
}