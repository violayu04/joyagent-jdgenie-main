#!/usr/bin/env python3
"""
Quick test script to verify DashScope API connection
"""

import asyncio
import httpx
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

async def test_dashscope_api():
    """Test the DashScope API connection"""
    
    api_key = os.getenv("DASHSCOPE_API_KEY")
    if not api_key:
        print("❌ DASHSCOPE_API_KEY not found in environment")
        return False
    
    print(f"✅ API Key found: {api_key[:10]}...")
    
    # Test payload
    payload = {
        "model": "qwen-turbo",
        "input": {
            "messages": [
                {
                    "role": "user",
                    "content": "Hello, please respond with 'API connection successful'"
                }
            ]
        },
        "parameters": {
            "temperature": 0.1,
            "max_tokens": 50
        }
    }
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            print("🔄 Testing API connection...")
            response = await client.post(
                "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                headers=headers,
                json=payload
            )
            
            print(f"📋 Response status: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                print("✅ API connection successful!")
                
                if "output" in result and "text" in result["output"]:
                    print(f"📝 Response: {result['output']['text']}")
                else:
                    print(f"📝 Raw response: {result}")
                return True
            else:
                print(f"❌ API request failed with status: {response.status_code}")
                print(f"📝 Response headers: {dict(response.headers)}")
                print(f"📝 Response body: {response.text}")
                return False
                
    except Exception as e:
        print(f"❌ Exception occurred: {e}")
        return False

if __name__ == "__main__":
    success = asyncio.run(test_dashscope_api())
    if success:
        print("\n🎉 All tests passed! Your API is working correctly.")
    else:
        print("\n💥 Tests failed. Please check your API key and network connection.")
