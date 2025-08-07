package com.jd.genie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "vector")
public class VectorConfig {
    
    /**
     * 向量数据库配置
     */
    private Database database = new Database();
    
    /**
     * 嵌入模型配置
     */
    private Embedding embedding = new Embedding();
    
    /**
     * 文本分块配置
     */
    private Chunking chunking = new Chunking();
    
    @Data
    public static class Database {
        private String type = "chroma"; // chroma, qdrant, milvus
        private String host = "localhost";
        private Integer port = 8000;
        private String collection = "genie_documents";
        private String apiKey;
    }
    
    @Data
    public static class Embedding {
        private String provider = "openai"; // openai, huggingface, local
        private String model = "text-embedding-ada-002";
        private String apiKey;
        private String baseUrl;
        private Integer dimension = 1536;
        private Integer maxTokens = 8191;
    }
    
    @Data
    public static class Chunking {
        private Integer chunkSize = 1000;
        private Integer chunkOverlap = 200;
        private String strategy = "recursive"; // recursive, sentence, paragraph
        private String separators = "\n\n,\n, ,。,！,？";
    }
}