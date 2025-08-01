#!/bin/bash

echo "🔧 Setting up JoyAgent Document Analysis..."

# Navigate to the genie-client directory
cd /Users/yuhaiyan/Desktop/joyagent-jdgenie-main/genie-client

# Check if virtual environment exists
if [ ! -d ".venv" ]; then
    echo "📦 Creating virtual environment..."
    python3 -m venv .venv
fi

# Activate virtual environment
echo "🔄 Activating virtual environment..."
source .venv/bin/activate

# Install/upgrade dependencies
echo "📥 Installing dependencies..."
pip install --upgrade pip
pip install python-dotenv httpx

# Test API connection
echo "🧪 Testing API connection..."
python test_api.py

echo "✅ Setup complete!"
