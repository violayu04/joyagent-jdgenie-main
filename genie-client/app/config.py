import os
from functools import lru_cache
from pydantic_settings import BaseSettings

# 默认配置常量
DEFAULT_TIMEOUT = 5
DEFAULT_SSE_READ_TIMEOUT = 60 * 5  # 5分钟
MAX_TIMEOUT_MINUTES = 15
TIMEOUT_MULTIPLIER = 60

HEADER_COOKIE = "Cookie"
HEADER_TIMEOUT = "Timeout"
HEADER_SERVER_KEYS = "X-Server-Keys"

class Settings(BaseSettings):
    """Application settings"""
    # Document Analysis Settings
    dashscope_api_key: str = os.getenv("DASHSCOPE_API_KEY", "")
    upload_directory: str = "uploads"
    max_file_size_mb: int = 50
    supported_file_types: list = [".pdf", ".docx", ".doc", ".txt", ".csv", ".json", ".md"]
    
    # LLM API Settings
    qwen_model: str = os.getenv("LLM_MODEL", "qwen-max")
    qwen_base_url: str = os.getenv("LLM_API_BASE", "https://dashscope.aliyuncs.com/compatible-mode/v1")
    analysis_timeout: int = int(os.getenv("ANALYSIS_TIMEOUT", "120"))
    
    # Content Processing
    max_content_length: int = 32000
    truncate_content: bool = True
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"

@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance"""
    return Settings()
