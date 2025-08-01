#!/usr/bin/env python3
"""
Comprehensive DashScope API diagnosis script
"""

import asyncio
import httpx
import os
import json
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

async def diagnose_api_issues():
    """Comprehensive diagnosis of API issues"""
    
    print("🔍 DashScope API Diagnosis")
    print("=" * 50)
    
    # 1. Check environment variables
    print("1️⃣ Environment Variables Check:")
    api_key = os.getenv("DASHSCOPE_API_KEY")
    qwen_model = os.getenv("QWEN_MODEL", "qwen-turbo")
    base_url = os.getenv("QWEN_BASE_URL")
    
    if not api_key:
        print("❌ DASHSCOPE_API_KEY not found")
        return False
    
    print(f"✅ API Key found: {api_key[:15]}...{api_key[-4:]}")
    print(f"✅ Model: {qwen_model}")
    print(f"✅ Base URL: {base_url}")
    
    # 2. Check API key format
    print("\n2️⃣ API Key Format Check:")
    if api_key.startswith("sk-"):
        print("⚠️  API key starts with 'sk-' (OpenAI format)")
        print("💡 DashScope keys usually don't start with 'sk-'")
        print("💡 Please verify this is a valid DashScope API key")
    else:
        print("✅ API key format looks like DashScope format")
    
    # 3. Test different endpoints and formats
    endpoints_to_test = [
        {
            "name": "DashScope Text Generation (our current)",
            "url": "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
            "payload": {
                "model": qwen_model,
                "input": {
                    "messages": [
                        {
                            "role": "user",
                            "content": "Hello, respond with 'test successful'"
                        }
                    ]
                },
                "parameters": {
                    "temperature": 0.1,
                    "max_tokens": 50
                }
            }
        },
        {
            "name": "DashScope Compatible Mode",
            "url": "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "payload": {
                "model": qwen_model,
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello, respond with 'test successful'"
                    }
                ],
                "temperature": 0.1,
                "max_tokens": 50
            }
        }
    ]
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    for i, endpoint in enumerate(endpoints_to_test, 3):
        print(f"\n{i}️⃣ Testing {endpoint['name']}:")
        print(f"URL: {endpoint['url']}")
        
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    endpoint['url'],
                    headers=headers,
                    json=endpoint['payload']
                )
                
                print(f"Status: {response.status_code}")
                
                if response.status_code == 200:
                    result = response.json()
                    print("✅ Success!")
                    print(f"Response: {json.dumps(result, indent=2, ensure_ascii=False)[:500]}...")
                    return True
                else:
                    print(f"❌ Failed: {response.status_code}")
                    print(f"Headers: {dict(response.headers)}")
                    try:
                        error_detail = response.json()
                        print(f"Error detail: {json.dumps(error_detail, indent=2, ensure_ascii=False)}")
                    except:
                        print(f"Raw response: {response.text}")
                        
        except Exception as e:
            print(f"❌ Exception: {e}")
    
    # 4. Additional checks
    print(f"\n{len(endpoints_to_test)+3}️⃣ Additional Recommendations:")
    print("💡 Verify your API key at: https://dashscope.console.aliyun.com/")
    print("💡 Ensure you have sufficient quota/credits")
    print("💡 Check if your account region matches the endpoint")
    print("💡 DashScope keys typically look like: sk-xxx... or start with other prefixes")
    
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

if __name__ == "__main__":
    print("Starting comprehensive diagnosis...")
    
    success = asyncio.run(diagnose_api_issues())
    asyncio.run(test_network_connectivity())
    
    print("\n" + "=" * 50)
    if success:
        print("🎉 Found a working configuration!")
    else:
        print("💥 All tests failed. Please check:")
        print("   1. Your API key is valid for DashScope")
        print("   2. Your account has sufficient quota")
        print("   3. The API key format is correct")
        print("   4. Your network can reach DashScope servers")
