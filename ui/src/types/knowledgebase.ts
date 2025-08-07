export interface KnowledgeBase {
  id: number;
  name: string;
  description?: string;
  documentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Document {
  documentId: string;
  filename: string;
  contentType: string;
  fileSize: number;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  errorMessage?: string;
  metadata?: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

export interface SearchResult {
  chunkId: string;
  content: string;
  score: number;
  chunkIndex: number;
  tokenCount: number;
  documentId: string;
  filename: string;
}

export interface CreateKnowledgeBaseRequest {
  name: string;
  description?: string;
}

export interface SearchRequest {
  query: string;
  topK?: number;
}