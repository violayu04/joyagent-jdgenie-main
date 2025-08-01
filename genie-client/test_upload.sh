#!/bin/bash

echo "🧪 Testing Document Analysis API..."

# Wait a moment for server to start
sleep 3

# Test 1: Check if server is running
echo "📋 Step 1: Checking server health..."
curl -s http://localhost:8188/health | python3 -m json.tool

echo -e "\n📋 Step 2: Checking supported formats..."
curl -s http://localhost:8188/v1/document/supported-formats | python3 -m json.tool

echo -e "\n📋 Step 3: Testing document upload and analysis..."
curl -X POST http://localhost:8188/v1/document/analyze \
  -F "query=What are the main topics and key financial metrics in this document?" \
  -F "session_id=test_session_123" \
  -F "analysis_type=financial" \
  -F "files=@/Users/yuhaiyan/Desktop/joyagent-jdgenie-main/test_document.txt" \
  | python3 -m json.tool

echo -e "\n✅ API test completed!"
