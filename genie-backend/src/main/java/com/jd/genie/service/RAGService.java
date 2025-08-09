package com.jd.genie.service;

import com.jd.genie.entity.User;
import com.jd.genie.service.KnowledgeBaseService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {
    
    private final KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 基于知识库增强用户查询
     * @param knowledgeBaseId 知识库ID
     * @param user 用户
     * @param originalQuery 原始查询
     * @param topK 检索结果数量
     * @return 增强后的查询
     */
    public EnhancedQuery enhanceQuery(Long knowledgeBaseId, User user, String originalQuery, int topK) {
        return enhanceQuery(knowledgeBaseId, user, originalQuery, topK, null);
    }
    
    /**
     * 基于知识库特定文档增强用户查询
     * @param knowledgeBaseId 知识库ID
     * @param user 用户
     * @param originalQuery 原始查询
     * @param topK 检索结果数量
     * @param documentIds 指定文档ID列表（可选）
     * @return 增强后的查询
     */
    public EnhancedQuery enhanceQuery(Long knowledgeBaseId, User user, String originalQuery, int topK, List<String> documentIds) {
        try {
            log.info("Enhancing query with knowledge base: knowledgeBaseId={}, query={}", knowledgeBaseId, originalQuery);
            
            // 1. 在知识库中搜索相关内容
            List<SearchResult> searchResults;
            if (documentIds != null && !documentIds.isEmpty()) {
                log.info("Searching in specific documents: {}", documentIds);
                if (knowledgeBaseId != null) {
                    // 如果指定了知识库ID，在该知识库内搜索特定文档
                    searchResults = knowledgeBaseService.searchInSpecificDocuments(
                            knowledgeBaseId, user, originalQuery, topK, documentIds);
                } else {
                    // 如果没有指定知识库ID，跨知识库搜索指定文档
                    searchResults = knowledgeBaseService.searchInSpecificDocumentsAcrossKB(
                            user, originalQuery, topK, documentIds);
                }
            } else if (knowledgeBaseId != null) {
                searchResults = knowledgeBaseService.searchInKnowledgeBase(
                        knowledgeBaseId, user, originalQuery, topK);
            } else {
                log.info("No knowledge base ID or document IDs provided");
                return new EnhancedQuery(originalQuery, "", new ArrayList<>());
            }
            
            if (searchResults.isEmpty()) {
                log.info("No relevant documents found in knowledge base");
                return new EnhancedQuery(originalQuery, "", searchResults);
            }
            
            // 2. 构建上下文信息
            String contextInfo = buildContextInfo(searchResults);
            
            // 3. 构建增强的提示词
            String enhancedPrompt = buildEnhancedPrompt(originalQuery, contextInfo);
            
            log.info("Query enhanced successfully with {} relevant documents", searchResults.size());
            
            return new EnhancedQuery(enhancedPrompt, contextInfo, searchResults);
            
        } catch (Exception e) {
            log.error("Failed to enhance query with knowledge base", e);
            // 如果RAG失败，返回原始查询
            return new EnhancedQuery(originalQuery, "", List.of());
        }
    }
    
    /**
     * 构建上下文信息
     */
    private String buildContextInfo(List<SearchResult> searchResults) {
        StringBuilder contextBuilder = new StringBuilder();
        
        contextBuilder.append("【相关知识库内容】\n\n");
        
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            
            contextBuilder.append(String.format("文档片段 %d (来源: %s, 相似度: %.2f%%):\n", 
                    i + 1, 
                    result.getDocument().getOriginalFilename(),
                    result.getScore() * 100));
            
            contextBuilder.append(result.getContent());
            contextBuilder.append("\n\n");
        }
        
        contextBuilder.append("【知识库内容结束】\n");
        
        return contextBuilder.toString();
    }
    
    /**
     * 构建增强的提示词
     */
    private String buildEnhancedPrompt(String originalQuery, String contextInfo) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("请基于以下知识库内容回答用户的问题。如果知识库内容中没有相关信息，请明确说明，并基于你的知识给出回答。\n\n");
        
        promptBuilder.append(contextInfo);
        
        promptBuilder.append("\n用户问题: ");
        promptBuilder.append(originalQuery);
        
        promptBuilder.append("\n\n请要求：");
        promptBuilder.append("\n1. 优先使用知识库中的信息来回答问题");
        promptBuilder.append("\n2. 如果使用了知识库内容，请在回答中注明信息来源");
        promptBuilder.append("\n3. 如果知识库内容不足以回答问题，请结合你的通用知识补充说明");
        promptBuilder.append("\n4. 确保回答准确、详细且有用");
        
        return promptBuilder.toString();
    }
    
    /**
     * 增强查询结果类
     */
    public static class EnhancedQuery {
        private final String enhancedPrompt;
        private final String contextInfo;
        private final List<SearchResult> searchResults;
        
        public EnhancedQuery(String enhancedPrompt, String contextInfo, List<SearchResult> searchResults) {
            this.enhancedPrompt = enhancedPrompt;
            this.contextInfo = contextInfo;
            this.searchResults = searchResults;
        }
        
        public String getEnhancedPrompt() {
            return enhancedPrompt;
        }
        
        public String getContextInfo() {
            return contextInfo;
        }
        
        public List<SearchResult> getSearchResults() {
            return searchResults;
        }
        
        public boolean isEnhanced() {
            return !searchResults.isEmpty();
        }
        
        public List<String> getSourceDocuments() {
            return searchResults.stream()
                    .map(result -> result.getDocument().getOriginalFilename())
                    .distinct()
                    .collect(Collectors.toList());
        }
        
        public int getRelevantChunksCount() {
            return searchResults.size();
        }
    }
}