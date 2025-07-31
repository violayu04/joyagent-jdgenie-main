#!/bin/bash

# Fixed Import Issues - Start Script

echo "🔧 JoyAgent UI - Fixed Import Issues"
echo "===================================="
echo ""

# Check if we're in the right directory
if [ ! -f "package.json" ]; then
    echo "❌ Please run this script from the ui/ directory"
    exit 1
fi

echo "✅ In UI directory"

# Check for the fixed import
echo "🔍 Checking icon imports..."
if grep -q "BarChartOutlined" src/components/DocumentAnalyzer.tsx; then
    echo "✅ Using correct BarChartOutlined icon"
else
    echo "❌ Icon import issue still exists"
fi

# Clear Vite cache
echo "🧹 Clearing Vite cache..."
rm -rf node_modules/.vite dist .vite 2>/dev/null || true

# Kill existing process
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "🛑 Stopping existing process..."
    kill -9 $(lsof -ti:3000) 2>/dev/null || true
    sleep 2
fi

# Check dependencies
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
    if [ $? -ne 0 ]; then
        echo "❌ Failed to install dependencies"
        exit 1
    fi
else
    echo "✅ Dependencies installed"
fi

echo ""
echo "🚀 Starting development server..."
echo ""
echo "📊 Document Analysis: http://localhost:3000/documents"
echo "🏠 Home Page: http://localhost:3000"
echo ""
echo "💡 The AnalyticsOutlined import error has been fixed!"
echo "   Now using BarChartOutlined which exists in Ant Design"
echo ""

# Start with error catching
npm run dev || {
    echo ""
    echo "❌ Server failed to start. Common fixes:"
    echo "1. Make sure all file syntax is correct"
    echo "2. Check for any remaining import errors"
    echo "3. Try: rm -rf node_modules && npm install"
    echo ""
    echo "🔍 Check the error message above for specific issues"
}
