from datetime import datetime
from typing import List, Optional
from fastapi import FastAPI, Request, Body, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.client import SseClient
from app.header import HeaderEntity
from app.logger import default_logger as logger
from app.document_analyzer import DocumentAnalysisManager, AnalysisRequest
from app.config import get_settings

# Global variables for document analysis manager
document_manager: Optional[DocumentAnalysisManager] = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manage application lifespan"""
    global document_manager
    
    # Startup
    settings = get_settings()
    if settings.dashscope_api_key:
        document_manager = DocumentAnalysisManager(
            api_key=settings.dashscope_api_key,
            upload_directory=settings.upload_directory
        )
        logger.info(f"Document analysis manager initialized with {settings.qwen_model} via {settings.qwen_base_url}")
    else:
        logger.warning("DASHSCOPE_API_KEY not found - document analysis disabled")
    
    yield
    
    # Shutdown
    if document_manager:
        await document_manager.close()
        logger.info("Document analysis manager closed")

app = FastAPI(
    title="Genie MCP Client API",
    version="0.1.0",
    description="A lightweight web service for Model Context Protocol (MCP) server communication and document analysis",
    contact={
        "name": "Your Name/Team",
        "email": "your-email@example.com",
    },
    license_info={
        "name": "MIT",
    },
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, replace with specific origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health_check():
    """
    - 健康检查接口
    """
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "version": "0.1.0",
        "document_analysis_enabled": document_manager is not None
    }


@app.get("/v1/document/supported-formats")
async def get_supported_formats():
    """
    - 获取支持的文档格式
    """
    if not document_manager:
        raise HTTPException(status_code=503, detail="Document analysis service not available")
    
    try:
        formats = document_manager.get_supported_formats()
        return {
            "code": 200,
            "message": "success",
            "data": {
                "supported_formats": formats,
                "max_file_size_mb": get_settings().max_file_size_mb
            }
        }
    except Exception as e:
        logger.error(f"Error getting supported formats: {e}")
        return {
            "code": 500,
            "message": f"Error: {str(e)}",
            "data": None
        }


@app.post("/v1/document/analyze")
async def analyze_documents(
    request: Request,
    query: str = Form(..., description="Analysis query"),
    session_id: str = Form(..., description="Session ID"),
    user_id: Optional[str] = Form(None, description="User ID"),
    analysis_type: str = Form("general", description="Analysis type"),
    files: List[UploadFile] = File(..., description="Documents to analyze")
):
    """
    - 分析上传的文档
    """
    if not document_manager:
        raise HTTPException(status_code=503, detail="Document analysis service not available")
    
    logger.info(f"Document analysis request - Session: {session_id}, Query: {query[:100]}..., Files: {len(files)}")
    
    try:
        # Validate files
        if not files or len(files) == 0:
            return {
                "code": 400,
                "message": "No files provided",
                "data": None
            }
        
        # Check file count limit
        if len(files) > 10:  # Limit to 10 files per request
            return {
                "code": 400,
                "message": "Too many files. Maximum 10 files per request.",
                "data": None
            }
        
        # Create analysis request
        analysis_request = AnalysisRequest(
            query=query,
            files=[],  # Will be populated during processing
            session_id=session_id,
            user_id=user_id,
            analysis_type=analysis_type
        )
        
        # Analyze documents
        results = await document_manager.analyze_documents(analysis_request, files)
        
        # Convert results to serializable format
        serialized_results = [result.to_dict() for result in results]
        
        # Calculate statistics
        successful_analyses = sum(1 for r in results if r.success)
        
        return {
            "code": 200,
            "message": "success",
            "data": {
                "total_files": len(results),
                "successful_analyses": successful_analyses,
                "results": serialized_results,
                "query": query,
                "session_id": session_id,
                "timestamp": datetime.now().isoformat()
            }
        }
        
    except Exception as e:
        logger.error(f"Error analyzing documents: {e}")
        return {
            "code": 500,
            "message": f"Error analyzing documents: {str(e)}",
            "data": None
        }


@app.post("/v1/document/analyze-batch")
async def analyze_documents_batch(
    request: Request,
    analyses: List[dict] = Body(..., description="List of analysis requests")
):
    """
    - 批量分析文档（适用于已上传的文档）
    """
    if not document_manager:
        raise HTTPException(status_code=503, detail="Document analysis service not available")
    
    logger.info(f"Batch analysis request - {len(analyses)} analyses")
    
    try:
        results = []
        
        for analysis_data in analyses:
            # This would be used for pre-uploaded files
            # Implementation depends on how you want to handle file storage
            pass
        
        return {
            "code": 200,
            "message": "success",
            "data": {
                "results": results,
                "timestamp": datetime.now().isoformat()
            }
        }
        
    except Exception as e:
        logger.error(f"Error in batch analysis: {e}")
        return {
            "code": 500,
            "message": f"Error in batch analysis: {str(e)}",
            "data": None
        }


@app.post("/v1/document/clear-cache")
async def clear_document_cache(request: Request):
    """
    - 清除文档处理缓存
    """
    if not document_manager:
        raise HTTPException(status_code=503, detail="Document analysis service not available")
    
    try:
        document_manager.clear_cache()
        return {
            "code": 200,
            "message": "Cache cleared successfully",
            "data": {}
        }
    except Exception as e:
        logger.error(f"Error clearing cache: {e}")
        return {
            "code": 500,
            "message": f"Error clearing cache: {str(e)}",
            "data": None
        }


@app.post("/v1/serv/pong")
async def ping_server(
        request: Request,
        server_url: str = Body(..., embed=True, description="mcp server url", alias="server_url"),
):
    """
    - 根据请求 server_url 测试 server 的连通性
    """
    logger.info(f"方法:/v1/serv/pong, {server_url}, request headers: {request.headers}")
    mcp_client = SseClient(server_url=server_url, entity=HeaderEntity(request.headers))
    try:
        await mcp_client.ping_server()
        return {
            "code": 200,
            "message": "success",
            "data": {},
        }
    except Exception as e:
        logger.error(f"Error ping server: {str(e)}")
        return {
            "code": 500,
            "message": f"Error: {str(e)}",
            "data": None,
        }


@app.post("/v1/tool/list")
async def list_tools(
        request: Request,
        server_url: str = Body(..., embed=True, description="mcp server url", alias="server_url"),
):
    """
    - 根据请求 server_url 查询 tools 列表
    """
    logger.info(f"方法:/v1/tool/list, {server_url}, request headers: {request.headers}")
    mcp_client = SseClient(server_url=server_url, entity=HeaderEntity(request.headers))
    try:
        tools = await mcp_client.list_tools()
        return {
            "code": 200,
            "message": "success",
            "data": tools,
        }
    except Exception as e:
        logger.error(f"Error list tool: {str(e)}")
        return {
            "code": 500,
            "message": f"Error: {str(e)}",
            "data": None,
        }


@app.post("/v1/tool/call")
async def call_tool(
        request: Request,
        server_url: str = Body(..., description="mcp server url", alias="server_url"),
        name: str = Body(..., description="tool name to call", alias="name"),
        arguments: dict = Body(..., description="tool parameters", alias="arguments"),
):
    """
    - 调用指定工具
    """
    logger.info(f"方法: /v1/tool/call, {name} with arguments: {arguments}")
    logger.info(f"call: {server_url}, request headers: {request.headers}")
    entity = HeaderEntity(request.headers)
    if arguments is not None and arguments.get("Cookie") is not None:
        entity.append_cookie(arguments.get("Cookie"))
    mcp_client = SseClient(server_url=server_url, entity=entity)
    try:
        result = await mcp_client.call_tool(name, arguments)
        return {
            "code": 200,
            "message": "success",
            "data": result,
        }
    except Exception as e:
        logger.error(f"Error calling tool {name}: {str(e)}")
        return {
            "code": 500,
            "message": f"Error calling tool {name}: {str(e)}",
            "data": None,
        }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8188)
