#!/usr/bin/env python3
"""
Simple Document Analysis Server - Minimal Dependencies Version
Only requires: fastapi, uvicorn, httpx, python-multipart
"""

import os
import json
import tempfile
from datetime import datetime
from typing import List, Optional, Dict, Any
from pathlib import Path

from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import httpx

# Configuration
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "")
DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

app = FastAPI(
    title="JoyAgent Document Analysis API",
    version="0.1.0",
    description="Simple document analysis service for banking environments"
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://127.0.0.1:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Simple supported formats
SUPPORTED_FORMATS = {
    ".pdf": "application/pdf",
    ".txt": "text/plain",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".csv": "text/csv",
    ".json": "application/json",
    ".md": "text/markdown"
}

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "version": "0.1.0",
        "document_analysis_enabled": bool(DASHSCOPE_API_KEY)
    }

@app.get("/v1/document/supported-formats")
async def get_supported_formats():
    """Get supported file formats"""
    return {
        "code": 200,
        "message": "success",
        "data": {
            "supported_formats": SUPPORTED_FORMATS,
            "max_file_size_mb": 50
        }
    }

def extract_text_simple(file_content: bytes, filename: str) -> str:
    """Simple text extraction - works for txt files mainly"""
    try:
        # Try UTF-8 first
        text = file_content.decode('utf-8')
        return text
    except UnicodeDecodeError:
        try:
            # Try Latin-1 as fallback
            text = file_content.decode('latin-1')
            return text
        except:
            return f"Could not decode text from {filename}. File may be binary or use unsupported encoding."

async def analyze_with_qwen(content: str, query: str, filename: str) -> Dict[str, Any]:
    """Analyze content with Qwen LLM"""
    if not DASHSCOPE_API_KEY:
        return {
            "success": False,
            "error": "DASHSCOPE_API_KEY not configured"
        }
    
    # Build prompt
    prompt = f"""
Document Analysis Request:

DOCUMENT: {filename}
USER QUERY: {query}

DOCUMENT CONTENT:
{content[:4000]}  # Limit content length

Please analyze this document and answer the user's query. Provide a helpful and detailed response in Chinese.
"""
    
    payload = {
        "model": "qwen-turbo",
        "input": {
            "messages": [
                {
                    "role": "system",
                    "content": "你是一个专业的文档分析助手，为银行环境提供准确的文档分析服务。请用中文回答。"
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ]
        },
        "parameters": {
            "temperature": 0.1,
            "max_tokens": 1500,
            "top_p": 0.8
        }
    }
    
    headers = {
        "Authorization": f"Bearer {DASHSCOPE_API_KEY}",
        "Content-Type": "application/json"
    }
    
    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(DASHSCOPE_URL, headers=headers, json=payload)
            response.raise_for_status()
            
            result = response.json()
            analysis_text = result.get("output", {}).get("text", "分析完成，但未返回结果。")
            
            return {
                "success": True,
                "analysis": analysis_text,
                "metadata": {
                    "filename": filename,
                    "file_type": Path(filename).suffix.lower(),
                    "file_size": len(content.encode('utf-8')),
                    "word_count": len(content.split()),
                },
                "timestamp": datetime.now().isoformat()
            }
            
    except Exception as e:
        return {
            "success": False,
            "analysis": "",
            "error": f"Analysis failed: {str(e)}",
            "metadata": {
                "filename": filename,
                "file_type": Path(filename).suffix.lower(),
                "file_size": len(content.encode('utf-8')),
                "word_count": len(content.split()),
            },
            "timestamp": datetime.now().isoformat()
        }

@app.post("/v1/document/analyze")
async def analyze_documents(
    query: str = Form(..., description="Analysis query"),
    session_id: str = Form(..., description="Session ID"),
    user_id: Optional[str] = Form(None, description="User ID"),
    analysis_type: str = Form("general", description="Analysis type"),
    files: List[UploadFile] = File(..., description="Documents to analyze")
):
    """Analyze uploaded documents"""
    
    if not files:
        return {
            "code": 400,
            "message": "No files provided",
            "data": None
        }
    
    if len(files) > 10:
        return {
            "code": 400,
            "message": "Too many files. Maximum 10 files per request.",
            "data": None
        }
    
    results = []
    
    for file in files:
        try:
            # Read file content
            content_bytes = await file.read()
            
            # Simple text extraction
            content = extract_text_simple(content_bytes, file.filename or "unknown")
            
            # Analyze with Qwen
            result = await analyze_with_qwen(content, query, file.filename or "unknown")
            results.append(result)
            
        except Exception as e:
            results.append({
                "success": False,
                "analysis": "",
                "error": f"Failed to process {file.filename}: {str(e)}",
                "metadata": {
                    "filename": file.filename or "unknown",
                    "file_type": "unknown",
                    "file_size": 0,
                    "word_count": 0,
                },
                "timestamp": datetime.now().isoformat()
            })
    
    successful_analyses = sum(1 for r in results if r.get("success", False))
    
    return {
        "code": 200,
        "message": "success",
        "data": {
            "total_files": len(results),
            "successful_analyses": successful_analyses,
            "results": results,
            "query": query,
            "session_id": session_id,
            "timestamp": datetime.now().isoformat()
        }
    }

if __name__ == "__main__":
    import uvicorn
    
    print("🚀 Starting JoyAgent Document Analysis Service...")
    print(f"📋 DashScope API Key: {'✅ Configured' if DASHSCOPE_API_KEY else '❌ Missing'}")
    print(f"🌐 Server will run on: http://localhost:8188")
    print(f"📊 Health check: http://localhost:8188/health")
    print(f"📄 Supported formats: {list(SUPPORTED_FORMATS.keys())}")
    print("")
    
    uvicorn.run(app, host="0.0.0.0", port=8188)
