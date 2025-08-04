// Document Analysis Types for JoyAgent

export interface DocumentAnalysisResult {
  success: boolean;
  analysis: string;
  metadata: {
    filename: string;
    file_type: string;
    file_size: number;
    word_count: number;
    page_count?: number;
    upload_time?: string;
    content_hash?: string;
  };
  error?: string;
  timestamp: string;
  confidence_score?: number;
}

export interface AnalysisResponse {
  code: number;
  message: string;
  data: {
    total_files: number;
    successful_analyses: number;
    results: DocumentAnalysisResult[];
    query: string;
    session_id: string;
    timestamp: string;
  };
}

export interface SupportedFormatsResponse {
  code: number;
  message: string;
  data: {
    supported_formats: Record<string, string>;
    max_file_size_mb: number;
  };
}

export interface DocumentAnalyzerProps {
  sessionId: string;
  userId?: string;
  onAnalysisComplete?: (results: DocumentAnalysisResult[]) => void;
  className?: string;
}

export type AnalysisType = 'general' | 'financial' | 'compliance' | 'risk';

export interface AnalysisRequest {
  query: string;
  session_id: string;
  analysis_type: AnalysisType;
  user_id?: string;
  files: File[];
}

// API Endpoints
export const DOCUMENT_API_ENDPOINTS = {
  ANALYZE: '/api/v1/document/analyze',
  SUPPORTED_FORMATS: '/api/v1/document/supported-formats',
  CLEAR_CACHE: '/api/v1/document/clear-cache',
  HEALTH: '/health'
} as const;

// File type validation
export const SUPPORTED_FILE_EXTENSIONS = [
  '.pdf',
  '.docx', 
  '.doc',
  '.txt',
  '.csv',
  '.json',
  '.md',
  '.xml',
  '.html'
] as const;

export const MAX_FILE_SIZE_MB = 50;
export const MAX_FILES_PER_REQUEST = 10;