"""
Document Analysis System for JoyAgent
Handles file upload, content extraction, and LLM analysis for banking environments
"""

import os
import tempfile
import mimetypes
from pathlib import Path
from typing import List, Dict, Any, Optional, Union
import logging
import asyncio
from datetime import datetime
import hashlib
import json

# Document processing libraries
try:
    import PyPDF2
    PDF_AVAILABLE = True
except ImportError:
    PDF_AVAILABLE = False

try:
    from docx import Document
    DOCX_AVAILABLE = True
except ImportError:
    DOCX_AVAILABLE = False

try:
    import pandas as pd
    PANDAS_AVAILABLE = True
except ImportError:
    PANDAS_AVAILABLE = False

# For API integration
import httpx
from dataclasses import dataclass, asdict
from fastapi import UploadFile
import aiofiles

from app.logger import default_logger as logger
from app.config import get_settings

settings = get_settings()

@dataclass
class DocumentMetadata:
    """Metadata for uploaded documents"""
    filename: str
    file_type: str
    file_size: int
    upload_time: datetime
    content_hash: str
    page_count: Optional[int] = None
    word_count: Optional[int] = None
    extraction_status: str = "pending"
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        result = asdict(self)
        result['upload_time'] = self.upload_time.isoformat()
        return result

@dataclass
class AnalysisRequest:
    """Request structure for document analysis"""
    query: str
    files: List[str]  # File paths or content hashes
    session_id: str
    user_id: Optional[str] = None
    analysis_type: str = "general"  # general, financial, compliance, etc.

@dataclass
class AnalysisResult:
    """Result structure for document analysis"""
    success: bool
    analysis: str
    metadata: DocumentMetadata
    query: str
    timestamp: datetime
    error: Optional[str] = None
    confidence_score: Optional[float] = None
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        result = asdict(self)
        result['timestamp'] = self.timestamp.isoformat()
        result['metadata'] = self.metadata.to_dict()
        return result

class DocumentProcessor:
    """Handles document processing and content extraction"""
    
    SUPPORTED_EXTENSIONS = {
        '.pdf': 'application/pdf',
        '.txt': 'text/plain',
        '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        '.doc': 'application/msword',
        '.csv': 'text/csv',
        '.json': 'application/json',
        '.md': 'text/markdown',
        '.xml': 'application/xml',
        '.html': 'text/html'
    }
    
    def __init__(self, upload_directory: str = "uploads"):
        self.upload_directory = Path(upload_directory)
        self.upload_directory.mkdir(exist_ok=True, parents=True)
        
        # Create subdirectories for organization
        (self.upload_directory / "temp").mkdir(exist_ok=True)
        (self.upload_directory / "processed").mkdir(exist_ok=True)
        
        logger.info(f"DocumentProcessor initialized with directory: {self.upload_directory}")
        
    def validate_file(self, file_path: Path, max_size_mb: int = 50) -> tuple[bool, str]:
        """Validate uploaded file type and size"""
        if not file_path.exists():
            return False, "File does not exist"
            
        # Check file extension
        if file_path.suffix.lower() not in self.SUPPORTED_EXTENSIONS:
            return False, f"Unsupported file type: {file_path.suffix}"
            
        # Check file size
        file_size = file_path.stat().st_size
        max_size = max_size_mb * 1024 * 1024
        if file_size > max_size:
            return False, f"File too large: {file_size} bytes (max: {max_size} bytes)"
        
        # Check if required libraries are available for file type
        if file_path.suffix.lower() == '.pdf' and not PDF_AVAILABLE:
            return False, "PDF processing not available - install PyPDF2"
        
        if file_path.suffix.lower() in ['.docx', '.doc'] and not DOCX_AVAILABLE:
            return False, "DOCX processing not available - install python-docx"
            
        if file_path.suffix.lower() == '.csv' and not PANDAS_AVAILABLE:
            return False, "CSV processing not available - install pandas"
            
        return True, "Valid file"
    
    async def save_upload_file(self, upload_file: UploadFile) -> Path:
        """Save uploaded file to temporary location"""
        # Create secure filename
        safe_filename = self._secure_filename(upload_file.filename or "unnamed_file")
        temp_path = self.upload_directory / "temp" / f"{datetime.now().strftime('%Y%m%d_%H%M%S')}_{safe_filename}"
        
        async with aiofiles.open(temp_path, 'wb') as f:
            content = await upload_file.read()
            await f.write(content)
        
        logger.info(f"File saved to: {temp_path}")
        return temp_path
    
    def _secure_filename(self, filename: str) -> str:
        """Create a secure filename by removing dangerous characters"""
        import re
        # Remove path components
        filename = os.path.basename(filename)
        # Remove dangerous characters
        filename = re.sub(r'[^\w\s.-]', '', filename)
        # Replace spaces with underscores
        filename = re.sub(r'\s+', '_', filename)
        # Limit length
        if len(filename) > 100:
            name, ext = os.path.splitext(filename)
            filename = name[:95] + ext
        return filename or "unnamed_file"
    
    async def extract_text_from_pdf(self, file_path: Path) -> tuple[str, int]:
        """Extract text content from PDF files"""
        if not PDF_AVAILABLE:
            raise ImportError("PyPDF2 not available")
            
        try:
            with open(file_path, 'rb') as file:
                pdf_reader = PyPDF2.PdfReader(file)
                text = ""
                page_count = len(pdf_reader.pages)
                
                for page in pdf_reader.pages:
                    text += page.extract_text() + "\n"
                
                return text.strip(), page_count
        except Exception as e:
            logger.error(f"Error extracting PDF text: {e}")
            raise
    
    async def extract_text_from_docx(self, file_path: Path) -> str:
        """Extract text content from DOCX files"""
        if not DOCX_AVAILABLE:
            raise ImportError("python-docx not available")
            
        try:
            doc = Document(file_path)
            text = []
            for paragraph in doc.paragraphs:
                text.append(paragraph.text)
            return "\n".join(text).strip()
        except Exception as e:
            logger.error(f"Error extracting DOCX text: {e}")
            raise
    
    async def extract_text_from_txt(self, file_path: Path) -> str:
        """Extract text content from plain text files"""
        try:
            async with aiofiles.open(file_path, 'r', encoding='utf-8') as file:
                return (await file.read()).strip()
        except UnicodeDecodeError:
            # Try with different encoding
            async with aiofiles.open(file_path, 'r', encoding='latin-1') as file:
                return (await file.read()).strip()
        except Exception as e:
            logger.error(f"Error reading text file: {e}")
            raise
    
    async def extract_text_from_csv(self, file_path: Path) -> str:
        """Extract and format CSV content"""
        if not PANDAS_AVAILABLE:
            raise ImportError("pandas not available")
            
        try:
            df = pd.read_csv(file_path)
            # Convert DataFrame to readable text format
            summary = f"CSV Data Summary:\nColumns: {list(df.columns)}\nRows: {len(df)}\n\n"
            
            # Include first 10 rows and basic statistics
            summary += f"First 10 rows:\n{df.head(10).to_string()}\n\n"
            
            # Add basic statistics for numeric columns
            numeric_cols = df.select_dtypes(include=['number']).columns
            if len(numeric_cols) > 0:
                summary += f"Numeric column statistics:\n{df[numeric_cols].describe().to_string()}"
            
            return summary
        except Exception as e:
            logger.error(f"Error reading CSV file: {e}")
            raise
    
    async def extract_text_from_json(self, file_path: Path) -> str:
        """Extract and format JSON content"""
        try:
            async with aiofiles.open(file_path, 'r', encoding='utf-8') as file:
                content = await file.read()
                data = json.loads(content)
                return f"JSON Content:\n{json.dumps(data, indent=2, ensure_ascii=False)}"
        except Exception as e:
            logger.error(f"Error reading JSON file: {e}")
            raise
    
    async def extract_content(self, file_path: Path) -> tuple[str, Optional[int]]:
        """Extract text content based on file type"""
        file_extension = file_path.suffix.lower()
        page_count = None
        
        try:
            if file_extension == '.pdf':
                content, page_count = await self.extract_text_from_pdf(file_path)
            elif file_extension in ['.docx', '.doc']:
                content = await self.extract_text_from_docx(file_path)
            elif file_extension in ['.txt', '.md']:
                content = await self.extract_text_from_txt(file_path)
            elif file_extension == '.csv':
                content = await self.extract_text_from_csv(file_path)
            elif file_extension == '.json':
                content = await self.extract_text_from_json(file_path)
            else:
                # Try to read as text file
                content = await self.extract_text_from_txt(file_path)
            
            return content, page_count
            
        except Exception as e:
            logger.error(f"Error extracting content from {file_path}: {e}")
            raise
    
    async def generate_metadata(self, file_path: Path, content: str, page_count: Optional[int] = None) -> DocumentMetadata:
        """Generate metadata for the uploaded document"""
        stat = file_path.stat()
        
        # Generate content hash for deduplication
        content_hash = hashlib.md5(content.encode('utf-8')).hexdigest()
        
        # Count words
        word_count = len(content.split()) if content else 0
        
        return DocumentMetadata(
            filename=file_path.name,
            file_type=file_path.suffix.lower(),
            file_size=stat.st_size,
            upload_time=datetime.now(),
            content_hash=content_hash,
            page_count=page_count,
            word_count=word_count,
            extraction_status="completed"
        )

class QwenAnalyzer:
    """Interface with Qwen LLM via DashScope API for document analysis"""
    
    def __init__(self, api_key: str, base_url: str = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"):
        self.api_key = api_key
        self.base_url = base_url
        self.client = httpx.AsyncClient(timeout=60.0)
        
    async def analyze_document(self, content: str, query: str, metadata: DocumentMetadata) -> AnalysisResult:
        """Send document content to Qwen for analysis"""
        
        # Prepare context-aware prompt
        prompt = self._build_analysis_prompt(content, query, metadata)
        
        payload = {
            "model": settings.qwen_model,  # Use configurable model
            "input": {
                "messages": [
                    {
                        "role": "system",
                        "content": "You are a document analysis assistant in a banking environment. Provide thorough, accurate analysis while maintaining confidentiality and compliance standards. Focus on financial metrics, risk factors, compliance issues, and business insights."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ]
            },
            "parameters": {
                "temperature": 0.1,  # Low temperature for consistent analysis
                "max_tokens": 2000,
                "top_p": 0.8
            }
        }
        
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        try:
            response = await self.client.post(self.base_url, headers=headers, json=payload)
            response.raise_for_status()
            
            result = response.json()
            analysis_text = result.get("output", {}).get("text", "")
            
            return AnalysisResult(
                success=True,
                analysis=analysis_text,
                metadata=metadata,
                query=query,
                timestamp=datetime.now(),
                confidence_score=0.85  # Mock confidence score
            )
            
        except httpx.HTTPError as e:
            logger.error(f"API request failed: {e}")
            return AnalysisResult(
                success=False,
                analysis="",
                metadata=metadata,
                query=query,
                timestamp=datetime.now(),
                error=f"API request failed: {str(e)}"
            )
        except Exception as e:
            logger.error(f"Unexpected error during analysis: {e}")
            return AnalysisResult(
                success=False,
                analysis="",
                metadata=metadata,
                query=query,
                timestamp=datetime.now(),
                error=f"Analysis failed: {str(e)}"
            )
    
    def _build_analysis_prompt(self, content: str, query: str, metadata: DocumentMetadata) -> str:
        """Build context-aware prompt for document analysis"""
        
        # Truncate content if too long (preserve beginning and end)
        max_content_length = settings.max_content_length  # Use configurable limit
        if len(content) > max_content_length:
            middle_point = max_content_length // 2
            content = content[:middle_point] + "\n\n[CONTENT TRUNCATED FOR LENGTH]\n\n" + content[-middle_point:]
        
        prompt = f"""
Document Analysis Request for Banking Environment:

DOCUMENT METADATA:
- Filename: {metadata.filename}
- File Type: {metadata.file_type}
- File Size: {metadata.file_size} bytes
- Word Count: {metadata.word_count}
- Upload Time: {metadata.upload_time}
{f"- Page Count: {metadata.page_count}" if metadata.page_count else ""}

USER QUERY:
{query}

DOCUMENT CONTENT:
{content}

ANALYSIS INSTRUCTIONS:
Please analyze this document in the context of the user's query. Consider the following aspects for banking/financial analysis:
1. Financial metrics and key performance indicators
2. Risk factors and compliance considerations
3. Business insights and strategic implications
4. Data accuracy and reliability
5. Regulatory compliance indicators

Provide a comprehensive response that addresses the specific question while maintaining confidentiality and focusing on actionable insights.
"""
        return prompt
    
    async def close(self):
        """Close the HTTP client"""
        await self.client.aclose()

class DocumentAnalysisManager:
    """Main class to manage the document analysis workflow"""
    
    def __init__(self, api_key: str, upload_directory: str = "uploads"):
        self.processor = DocumentProcessor(upload_directory)
        self.analyzer = QwenAnalyzer(api_key)
        self.processed_documents: Dict[str, tuple[str, DocumentMetadata]] = {}  # content_hash -> (content, metadata)
        
    async def process_uploaded_file(self, upload_file: UploadFile) -> tuple[str, DocumentMetadata]:
        """Process a single uploaded file and return content and metadata"""
        
        # Save file temporarily
        temp_path = await self.processor.save_upload_file(upload_file)
        
        try:
            # Validate file
            is_valid, message = self.processor.validate_file(temp_path)
            if not is_valid:
                raise ValueError(f"File validation failed: {message}")
            
            # Extract content
            logger.info(f"Extracting content from {temp_path.name}")
            content, page_count = await self.processor.extract_content(temp_path)
            
            if not content.strip():
                raise ValueError("Could not extract readable content from file")
            
            # Generate metadata
            metadata = await self.processor.generate_metadata(temp_path, content, page_count)
            
            # Cache the processed document
            self.processed_documents[metadata.content_hash] = (content, metadata)
            
            logger.info(f"Successfully processed {temp_path.name}: {metadata.word_count} words")
            return content, metadata
            
        finally:
            # Clean up temporary file
            try:
                temp_path.unlink()
            except Exception as e:
                logger.warning(f"Failed to clean up temporary file {temp_path}: {e}")
    
    async def analyze_documents(self, request: AnalysisRequest, upload_files: List[UploadFile]) -> List[AnalysisResult]:
        """Analyze multiple documents with a query"""
        results = []
        
        for upload_file in upload_files:
            try:
                # Process the file
                content, metadata = await self.process_uploaded_file(upload_file)
                
                # Analyze with Qwen
                logger.info(f"Analyzing document: {metadata.filename}")
                analysis_result = await self.analyzer.analyze_document(content, request.query, metadata)
                results.append(analysis_result)
                
            except Exception as e:
                logger.error(f"Error processing file {upload_file.filename}: {e}")
                
                # Create error metadata
                error_metadata = DocumentMetadata(
                    filename=upload_file.filename or "unknown",
                    file_type="unknown",
                    file_size=0,
                    upload_time=datetime.now(),
                    content_hash="error",
                    extraction_status="failed"
                )
                
                results.append(AnalysisResult(
                    success=False,
                    analysis="",
                    metadata=error_metadata,
                    query=request.query,
                    timestamp=datetime.now(),
                    error=str(e)
                ))
        
        return results
    
    def get_supported_formats(self) -> Dict[str, str]:
        """Return supported file formats"""
        return self.processor.SUPPORTED_EXTENSIONS
    
    def clear_cache(self):
        """Clear processed documents cache"""
        self.processed_documents.clear()
        logger.info("Document cache cleared")
    
    async def close(self):
        """Clean up resources"""
        await self.analyzer.close()
