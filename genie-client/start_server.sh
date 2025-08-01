#!/bin/bash

echo "🚀 Starting JoyAgent Genie Client Server"
echo "========================================"

cd /Users/yuhaiyan/Desktop/joyagent-jdgenie-main/genie-client

# Check if virtual environment exists
if [ -d ".venv" ]; then
    echo "✅ Virtual environment found"
    source .venv/bin/activate
else
    echo "📦 Creating virtual environment..."
    python3 -m venv .venv
    source .venv/bin/activate
fi

# Install dependencies
echo "📥 Installing/updating dependencies..."
pip install --upgrade pip
pip install -e .

# Check configuration
echo "🔧 Checking configuration..."
python show_config.py

echo ""
echo "🌐 Starting server on http://localhost:8188..."
echo "Press Ctrl+C to stop the server"
echo ""

# Start the server
python server.py
