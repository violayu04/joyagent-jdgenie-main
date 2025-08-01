#!/usr/bin/env python3
"""
Test Local Qwen via Ollama
"""

import asyncio
import httpx
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

async def test_local_qwen():
    """Test local Qwen via Ollama"""
    
    print("🧪 Testing Local Qwen via Ollama")
    print("=" * 40)
    
    # Test payload
    payload = {
        "model": "qwen:7b",
        "messages": [
            {
                "role": "user",
                "content": "Hello, please respond with 'Local Qwen connection successful'"
            }
        ],
        "temperature": 0.1,
        "max_tokens": 50
    }
    
    headers = {
        "Content-Type": "application/json"
    }
    
    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            print("🔄 Testing local Ollama connection...")
            response = await client.post(
                "http://localhost:11434/v1/chat/completions",
                headers=headers,
                json=payload
            )
            
            print(f"📋 Response status: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                print("✅ Local Qwen connection successful!")
                
                if "choices" in result and len(result["choices"]) > 0:
                    message = result["choices"][0]["message"]["content"]
                    print(f"📝 Response: {message}")
                else:
                    print(f"📝 Raw response: {result}")
                return True
            else:
                print(f"❌ API request failed with status: {response.status_code}")
                print(f"📝 Response: {response.text}")
                return False
                
    except Exception as e:
        print(f"❌ Exception occurred: {e}")
        print("💡 Make sure Ollama is running: ollama serve")
        print("💡 Make sure Qwen model is installed: ollama pull qwen:7b")
        return False

if __name__ == "__main__":
    success = asyncio.run(test_local_qwen())
    if success:
        print("\n🎉 Local Qwen is working!")
        print("💡 Start your server with: python server.py")
    else:
        print("\n💥 Local setup failed. Try running:")
        print("   chmod +x setup_local_qwen.sh && ./setup_local_qwen.sh")
