#!/usr/bin/env python3
"""
Test OpenAI API connection
"""

import asyncio
import httpx
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

async def test_openai_api():
    """Test the OpenAI API connection"""
    
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        print("❌ OPENAI_API_KEY not found in environment")
        return False
    
    print(f"✅ API Key found: {api_key[:10]}...")
    
    # Test payload
    payload = {
        "model": "gpt-3.5-turbo",
        "messages": [
            {
                "role": "user",
                "content": "Hello, please respond with 'API connection successful'"
            }
        ],
        "temperature": 0.1,
        "max_tokens": 50
    }
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            print("🔄 Testing OpenAI API connection...")
            response = await client.post(
                "https://api.openai.com/v1/chat/completions",
                headers=headers,
                json=payload
            )
            
            print(f"📋 Response status: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                print("✅ OpenAI API connection successful!")
                
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
                elif response.status_code == 429:
                    print("💡 Rate limit exceeded - try again later")
                elif response.status_code == 403:
                    print("💡 Access forbidden - check your API key permissions")
                
                return False
                
    except Exception as e:
        print(f"❌ Exception occurred: {e}")
        return False

if __name__ == "__main__":
    success = asyncio.run(test_openai_api())
    if success:
        print("\n🎉 All tests passed! Your OpenAI API is working correctly.")
        print("💡 You can now upload files and analyze them with OpenAI!")
    else:
        print("\n💥 Tests failed. Please check:")
        print("   1. Your OpenAI API key is valid")
        print("   2. You have sufficient credits in your OpenAI account")
        print("   3. Your network can reach OpenAI servers")
        print("   4. Get a valid key from: https://platform.openai.com/api-keys")
