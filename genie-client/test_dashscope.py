#!/usr/bin/env python3
"""
Test DashScope API connection for Qwen
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
    qwen_model = os.getenv("LLM_MODEL", "qwen-max")
    qwen_base_url = os.getenv("LLM_API_BASE", "https://dashscope.aliyuncs.com/compatible-mode/v1")
    if not api_key:
        print("❌ DASHSCOPE_API_KEY not found in environment")
        print("💡 Please follow the setup guide in DASHSCOPE_SETUP.md")
        return False
    
    if api_key.strip() == "":
        print("❌ DASHSCOPE_API_KEY is empty")
        print("💡 Please add your DashScope API key to the .env file")
        return False
    
    print(f"✅ API Key found: {api_key[:15]}...{api_key[-4:]}")
    print(f"✅ Model: {qwen_model}")
    print(f"✅ API Base: {qwen_base_url}")
    
    # Test payload using OpenAI-compatible format
    payload = {
        "model": qwen_model,
        "messages": [
            {
                "role": "user",
                "content": "Hello, please respond with 'qwen-max connection successful'"
            }
        ],
        "temperature": 0.1,
        "max_tokens": 50
    }
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    # Test different endpoints
    endpoints = [
        {
            "name": "DashScope Compatible Mode",
            "url": f"{qwen_base_url}/chat/completions"
        }
    ]
    
    for endpoint in endpoints:
        print(f"\n🔄 Testing {endpoint['name']}...")
        print(f"URL: {endpoint['url']}")
        
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    endpoint['url'],
                    headers=headers,
                    json=payload
                )
                
                print(f"📋 Response status: {response.status_code}")
                
                if response.status_code == 200:
                    result = response.json()
                    print("✅ qwen-max API connection successful!")
                    
                    if "choices" in result and len(result["choices"]) > 0:
                        message = result["choices"][0]["message"]["content"]
                        print(f"📝 Response: {message}")
                    else:
                        print(f"📝 Raw response: {result}")
                    return True
                else:
                    print(f"❌ API request failed with status: {response.status_code}")
                    print(f"📝 Response headers: {dict(response.headers)}")
                    print(f"📝 Response body: {response.text}")
                    
                    # Common error analysis
                    if response.status_code == 401:
                        print("💡 This usually means your API key is invalid")
                        print("💡 Please check your DashScope console for the correct key")
                    elif response.status_code == 429:
                        print("💡 Rate limit exceeded - try again later")
                    elif response.status_code == 403:
                        print("💡 Access forbidden - check your API key permissions")
                    elif response.status_code == 400:
                        print("💡 Bad request - check the model name and payload format")
                    
                    return False
                    
        except Exception as e:
            print(f"❌ Exception occurred: {e}")
            return False
    
    return False

async def test_network_connectivity():
    """Test basic network connectivity to DashScope"""
    print("\n🌐 Network Connectivity Test:")
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get("https://dashscope.aliyuncs.com")
            print(f"✅ Can reach DashScope: {response.status_code}")
    except Exception as e:
        print(f"❌ Cannot reach DashScope: {e}")
        print("💡 Check your internet connection and firewall settings")

if __name__ == "__main__":
    print("🧪 DashScope API Connection Test")
    print("=" * 50)
    
    success = asyncio.run(test_dashscope_api())
    asyncio.run(test_network_connectivity())
    
    print("\n" + "=" * 50)
    if success:
        print("🎉 All tests passed! Your qwen-max API is working correctly.")
        print("💡 You can now upload files and analyze them with qwen-max!")
        print("💡 Start your server with: python server.py")
    else:
        print("💥 Tests failed. Please:")
        print("   1. Get a valid DashScope API key from: https://dashscope.console.aliyun.com/")
        print("   2. Add it to your .env file: DASHSCOPE_API_KEY=your-key-here")
        print("   3. Make sure you have sufficient credits in your account")
        print("   4. Check the setup guide: DASHSCOPE_SETUP.md")
