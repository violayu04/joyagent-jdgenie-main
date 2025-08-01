#!/usr/bin/env python3
"""
Simple server test to identify startup issues
"""

import sys
import os
import traceback

# Add current directory to path
sys.path.insert(0, '/Users/yuhaiyan/Desktop/joyagent-jdgenie-main/genie-client')

def test_server_startup():
    """Test server startup step by step"""
    
    print("🧪 Testing Server Startup")
    print("=" * 40)
    
    try:
        print("1️⃣ Testing basic imports...")
        from datetime import datetime
        from typing import List, Optional
        print("   ✅ Basic imports successful")
        
        print("2️⃣ Testing FastAPI imports...")
        from fastapi import FastAPI, Request, Body, UploadFile, File, Form, HTTPException
        from fastapi.responses import JSONResponse
        from fastapi.middleware.cors import CORSMiddleware
        from contextlib import asynccontextmanager
        print("   ✅ FastAPI imports successful")
        
        print("3️⃣ Testing app imports...")
        from app.config import get_settings
        print("   ✅ Config import successful")
        
        from app.logger import default_logger as logger
        print("   ✅ Logger import successful")
        
        from app.document_analyzer import DocumentAnalysisManager, AnalysisRequest
        print("   ✅ Document analyzer import successful")
        
        print("4️⃣ Testing configuration...")
        settings = get_settings()
        print(f"   ✅ Settings loaded")
        print(f"   📋 Qwen Model: {settings.qwen_model}")
        print(f"   📋 Qwen Base URL: {settings.qwen_base_url}")
        print(f"   📋 API Key set: {'Yes' if settings.dashscope_api_key else 'No'}")
        
        print("5️⃣ Testing FastAPI app creation...")
        
        @asynccontextmanager
        async def lifespan(app: FastAPI):
            print("   🔄 App lifespan starting...")
            yield
            print("   🔄 App lifespan ending...")
        
        app = FastAPI(
            title="Genie MCP Client API",
            version="0.1.0",
            lifespan=lifespan
        )
        print("   ✅ FastAPI app created successfully")
        
        print("6️⃣ Testing CORS middleware...")
        app.add_middleware(
            CORSMiddleware,
            allow_origins=["*"],
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )
        print("   ✅ CORS middleware added")
        
        print("7️⃣ Testing health endpoint...")
        @app.get("/health")
        async def health_check():
            return {
                "status": "healthy",
                "timestamp": datetime.now().isoformat(),
                "version": "0.1.0"
            }
        print("   ✅ Health endpoint added")
        
        print("\n🎉 All tests passed! Server should start successfully.")
        print("💡 Try running: python server.py")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Error during startup test:")
        print(f"   {type(e).__name__}: {e}")
        print(f"\n📋 Full traceback:")
        traceback.print_exc()
        return False

if __name__ == "__main__":
    test_server_startup()
