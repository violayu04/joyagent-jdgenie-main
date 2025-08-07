-- 知识库表
CREATE TABLE knowledge_bases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    user_id BIGINT NOT NULL,
    document_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 文档表
CREATE TABLE documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(255) UNIQUE NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    filename VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000),
    content_type VARCHAR(100),
    file_size BIGINT,
    content_hash VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PROCESSING', -- PROCESSING, COMPLETED, FAILED
    error_message TEXT,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE
);

-- 文档块表（用于存储分块后的文本）
CREATE TABLE document_chunks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chunk_id VARCHAR(255) UNIQUE NOT NULL,
    document_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    start_pos INTEGER,
    end_pos INTEGER,
    token_count INTEGER,
    embedding_id VARCHAR(255), -- 向量数据库中的ID
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

-- 向量搜索记录表（用于分析和优化）
CREATE TABLE vector_searches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT,
    query_text TEXT NOT NULL,
    query_embedding_id VARCHAR(255),
    search_results JSON,
    result_count INTEGER,
    search_time_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases(id) ON DELETE SET NULL
);

-- 创建索引
CREATE INDEX idx_knowledge_bases_user_id ON knowledge_bases(user_id);
CREATE INDEX idx_documents_kb_id ON documents(knowledge_base_id);
CREATE INDEX idx_document_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_document_chunks_embedding_id ON document_chunks(embedding_id);
CREATE INDEX idx_vector_searches_user_id ON vector_searches(user_id);
CREATE INDEX idx_vector_searches_kb_id ON vector_searches(knowledge_base_id);