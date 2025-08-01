#!/bin/bash

echo "🚀 Setting up Local Qwen with Ollama (Alternative to DashScope)"
echo "==============================================================="

# Check if Ollama is installed
if ! command -v ollama &> /dev/null; then
    echo "📥 Installing Ollama..."
    curl -fsSL https://ollama.ai/install.sh | sh
else
    echo "✅ Ollama is already installed"
fi

# Start Ollama service
echo "🔄 Starting Ollama service..."
ollama serve &
sleep 5

# Pull Qwen model
echo "📥 Downloading Qwen model (this may take a few minutes)..."
ollama pull qwen:7b

# Update .env file for local usage
echo "🔧 Updating configuration for local usage..."
cat > /Users/yuhaiyan/Desktop/joyagent-jdgenie-main/.env << 'EOF'
# JoyAgent Document Analysis Configuration

# DashScope API Configuration (Using Local Ollama)
DASHSCOPE_API_KEY=local-ollama-key

# Document Processing Settings
UPLOAD_DIRECTORY=uploads
MAX_FILE_SIZE_MB=50
MAX_CONTENT_LENGTH=32000
TRUNCATE_CONTENT=true

# Qwen Model Configuration (Local Ollama)
QWEN_MODEL=qwen:7b
QWEN_BASE_URL=http://localhost:11434/v1
ANALYSIS_TIMEOUT=120

# Banking Environment Security Settings
# Enable additional logging for compliance
ENABLE_AUDIT_LOGGING=true
# Restrict file types for security
STRICT_FILE_VALIDATION=true

# Service URLs (adjust for your deployment)
GENIE_CLIENT_URL=http://localhost:8188
GENIE_BACKEND_URL=http://localhost:8080
GENIE_UI_URL=http://localhost:3000
EOF

echo "✅ Local setup complete!"
echo "💡 Now you can use Qwen locally without any cloud API keys"
echo "💡 Test with: cd genie-client && python test_local_qwen.py"
echo "💡 Start server with: cd genie-client && python server.py"
