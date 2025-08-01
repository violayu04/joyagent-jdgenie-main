#!/usr/bin/env python3
"""
Show current LLM configuration
"""

import os
from dotenv import load_dotenv

load_dotenv()

def show_config():
    """Display current configuration"""
    
    print("🔧 Current JoyAgent LLM Configuration")
    print("=" * 50)
    
    # LLM Settings
    print("📡 LLM API Settings:")
    print(f"   LLM_API_BASE: {os.getenv('LLM_API_BASE', 'Not set')}")
    print(f"   LLM_MODEL: {os.getenv('LLM_MODEL', 'Not set')}")
    
    # API Key
    api_key = os.getenv('DASHSCOPE_API_KEY', '')
    if api_key:
        if api_key.strip() == "":
            print(f"   DASHSCOPE_API_KEY: ❌ Empty")
        else:
            print(f"   DASHSCOPE_API_KEY: ✅ Set ({api_key[:10]}...{api_key[-4:]})")
    else:
        print(f"   DASHSCOPE_API_KEY: ❌ Not set")
    
    # Other settings
    print(f"\n⚙️  Other Settings:")
    print(f"   UPLOAD_DIRECTORY: {os.getenv('UPLOAD_DIRECTORY', 'uploads')}")
    print(f"   MAX_FILE_SIZE_MB: {os.getenv('MAX_FILE_SIZE_MB', '50')}")
    print(f"   ANALYSIS_TIMEOUT: {os.getenv('ANALYSIS_TIMEOUT', '120')} seconds")
    
    print(f"\n🌐 Service URLs:")
    print(f"   GENIE_CLIENT_URL: {os.getenv('GENIE_CLIENT_URL', 'http://localhost:8188')}")
    print(f"   GENIE_BACKEND_URL: {os.getenv('GENIE_BACKEND_URL', 'http://localhost:8080')}")
    print(f"   GENIE_UI_URL: {os.getenv('GENIE_UI_URL', 'http://localhost:3000')}")
    
    print(f"\n💡 Next Steps:")
    if not os.getenv('DASHSCOPE_API_KEY') or os.getenv('DASHSCOPE_API_KEY').strip() == "":
        print("   1. Get your DashScope API key from: https://dashscope.console.aliyun.com/")
        print("   2. Add it to .env: DASHSCOPE_API_KEY=your-actual-key")
        print("   3. Test with: python test_dashscope.py")
    else:
        print("   1. Test your setup: python test_dashscope.py")
        print("   2. Start server: python server.py")
        print("   3. Upload documents and analyze with qwen-max!")

if __name__ == "__main__":
    show_config()
