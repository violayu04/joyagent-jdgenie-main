package com.jd.genie.service;

import com.jd.genie.config.VectorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextChunkingService {
    
    private final VectorConfig vectorConfig;
    
    /**
     * 将文本分块
     * @param text 原始文本
     * @return 文本块列表
     */
    public List<TextChunk> chunkText(String text) {
        VectorConfig.Chunking config = vectorConfig.getChunking();
        
        switch (config.getStrategy().toLowerCase()) {
            case "recursive":
                return recursiveChunking(text, config);
            case "sentence":
                return sentenceChunking(text, config);
            case "paragraph":
                return paragraphChunking(text, config);
            default:
                return recursiveChunking(text, config);
        }
    }
    
    /**
     * 递归分块策略
     */
    private List<TextChunk> recursiveChunking(String text, VectorConfig.Chunking config) {
        List<TextChunk> chunks = new ArrayList<>();
        String[] separators = config.getSeparators().split(",");
        
        List<String> textParts = splitTextRecursively(text, separators, config.getChunkSize(), config.getChunkOverlap());
        
        int startPos = 0;
        for (int i = 0; i < textParts.size(); i++) {
            String content = textParts.get(i).trim();
            if (!content.isEmpty()) {
                TextChunk chunk = new TextChunk();
                chunk.setContent(content);
                chunk.setChunkIndex(i);
                chunk.setStartPos(startPos);
                chunk.setEndPos(startPos + content.length());
                chunk.setTokenCount(estimateTokenCount(content));
                
                chunks.add(chunk);
            }
            startPos += content.length();
        }
        
        return chunks;
    }
    
    /**
     * 句子分块策略
     */
    private List<TextChunk> sentenceChunking(String text, VectorConfig.Chunking config) {
        List<TextChunk> chunks = new ArrayList<>();
        
        // 使用正则表达式按句子分割
        String[] sentences = text.split("(?<=[。！？.!?])\\s*");
        
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        int startPos = 0;
        int currentPos = 0;
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;
            
            // 检查是否需要创建新的chunk
            if (currentChunk.length() + sentence.length() > config.getChunkSize() && currentChunk.length() > 0) {
                // 创建当前chunk
                String content = currentChunk.toString().trim();
                if (!content.isEmpty()) {
                    TextChunk chunk = new TextChunk();
                    chunk.setContent(content);
                    chunk.setChunkIndex(chunkIndex++);
                    chunk.setStartPos(startPos);
                    chunk.setEndPos(startPos + content.length());
                    chunk.setTokenCount(estimateTokenCount(content));
                    
                    chunks.add(chunk);
                }
                
                // 重置chunk，保留overlap部分
                currentChunk = new StringBuilder();
                if (config.getChunkOverlap() > 0 && chunks.size() > 0) {
                    String lastContent = chunks.get(chunks.size() - 1).getContent();
                    String overlapText = getLastWords(lastContent, config.getChunkOverlap());
                    currentChunk.append(overlapText);
                }
                
                startPos = currentPos - currentChunk.length();
            }
            
            currentChunk.append(sentence).append(" ");
            currentPos += sentence.length() + 1;
        }
        
        // 添加最后一个chunk
        if (currentChunk.length() > 0) {
            String content = currentChunk.toString().trim();
            if (!content.isEmpty()) {
                TextChunk chunk = new TextChunk();
                chunk.setContent(content);
                chunk.setChunkIndex(chunkIndex);
                chunk.setStartPos(startPos);
                chunk.setEndPos(startPos + content.length());
                chunk.setTokenCount(estimateTokenCount(content));
                
                chunks.add(chunk);
            }
        }
        
        return chunks;
    }
    
    /**
     * 段落分块策略
     */
    private List<TextChunk> paragraphChunking(String text, VectorConfig.Chunking config) {
        List<TextChunk> chunks = new ArrayList<>();
        
        // 按双换行符分割段落
        String[] paragraphs = text.split("\n\n+");
        
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        int startPos = 0;
        int currentPos = 0;
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;
            
            // 检查是否需要创建新的chunk
            if (currentChunk.length() + paragraph.length() > config.getChunkSize() && currentChunk.length() > 0) {
                // 创建当前chunk
                String content = currentChunk.toString().trim();
                if (!content.isEmpty()) {
                    TextChunk chunk = new TextChunk();
                    chunk.setContent(content);
                    chunk.setChunkIndex(chunkIndex++);
                    chunk.setStartPos(startPos);
                    chunk.setEndPos(startPos + content.length());
                    chunk.setTokenCount(estimateTokenCount(content));
                    
                    chunks.add(chunk);
                }
                
                // 重置chunk
                currentChunk = new StringBuilder();
                startPos = currentPos;
            }
            
            currentChunk.append(paragraph).append("\n\n");
            currentPos += paragraph.length() + 2;
        }
        
        // 添加最后一个chunk
        if (currentChunk.length() > 0) {
            String content = currentChunk.toString().trim();
            if (!content.isEmpty()) {
                TextChunk chunk = new TextChunk();
                chunk.setContent(content);
                chunk.setChunkIndex(chunkIndex);
                chunk.setStartPos(startPos);
                chunk.setEndPos(startPos + content.length());
                chunk.setTokenCount(estimateTokenCount(content));
                
                chunks.add(chunk);
            }
        }
        
        return chunks;
    }
    
    /**
     * 递归分割文本
     */
    private List<String> splitTextRecursively(String text, String[] separators, int chunkSize, int overlap) {
        List<String> finalChunks = new ArrayList<>();
        
        // 第一步：使用第一个分隔符分割
        List<String> splits = Arrays.asList(text.split(Pattern.quote(separators[0])));
        
        // 第二步：处理每个分割后的文本
        for (String split : splits) {
            if (split.length() <= chunkSize) {
                finalChunks.add(split);
            } else {
                // 如果文本还是太长，使用下一个分隔符
                if (separators.length > 1) {
                    String[] nextSeparators = Arrays.copyOfRange(separators, 1, separators.length);
                    finalChunks.addAll(splitTextRecursively(split, nextSeparators, chunkSize, overlap));
                } else {
                    // 强制按字符分割
                    List<String> charChunks = splitByCharacter(split, chunkSize, overlap);
                    finalChunks.addAll(charChunks);
                }
            }
        }
        
        return finalChunks;
    }
    
    /**
     * 按字符强制分割
     */
    private List<String> splitByCharacter(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            
            start = Math.max(start + chunkSize - overlap, start + 1);
        }
        
        return chunks;
    }
    
    /**
     * 获取文本的最后几个单词（用于overlap）
     */
    private String getLastWords(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = words.length - 1; i >= 0; i--) {
            String word = words[i];
            if (result.length() + word.length() + 1 <= maxLength) {
                if (result.length() > 0) {
                    result.insert(0, " ");
                }
                result.insert(0, word);
            } else {
                break;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 估算token数量（简单实现）
     */
    private int estimateTokenCount(String text) {
        // 简单的token估算：中文字符算1个token，英文单词算1个token
        int chineseChars = 0;
        int englishWords = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseChars++;
            }
        }
        
        String englishText = text.replaceAll("[\\u4e00-\\u9fa5]", "");
        englishWords = englishText.trim().isEmpty() ? 0 : englishText.trim().split("\\s+").length;
        
        return chineseChars + englishWords;
    }
    
    /**
     * 文本块数据类
     */
    public static class TextChunk {
        private String content;
        private Integer chunkIndex;
        private Integer startPos;
        private Integer endPos;
        private Integer tokenCount;
        
        // Getters and setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public Integer getStartPos() { return startPos; }
        public void setStartPos(Integer startPos) { this.startPos = startPos; }
        
        public Integer getEndPos() { return endPos; }
        public void setEndPos(Integer endPos) { this.endPos = endPos; }
        
        public Integer getTokenCount() { return tokenCount; }
        public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    }
}