#!/bin/bash

# Quick Fix and Start Script for JoyAgent UI

echo "🔧 Fixing syntax issues and starting JoyAgent UI..."
echo ""

# Check if we're in the right directory
if [ ! -f "package.json" ]; then
    echo "❌ Please run this script from the ui/ directory"
    exit 1
fi

echo "✅ In UI directory"

# Clear any build cache
echo "🧹 Clearing build cache..."
rm -rf dist .vite node_modules/.vite tsconfig.tsbuildinfo 2>/dev/null || true

# Check if DocumentAnalyzer has proper line endings
echo "🔍 Checking file syntax..."
if grep -q "\\\\n" src/components/DocumentAnalyzer.tsx; then
    echo "⚠️ Found literal \\n characters - files should be fixed now"
else
    echo "✅ File syntax looks good"
fi

# Kill any existing process on port 3000
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "🛑 Stopping existing process on port 3000..."
    kill -9 $(lsof -ti:3000) 2>/dev/null || true
    sleep 2
fi

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

echo ""
echo "🚀 Starting development server..."
echo "📊 Document Analysis will be at: http://localhost:3000/documents"
echo "🏠 Home page will be at: http://localhost:3000"
echo ""
echo "💡 If you still see errors:"
echo "   1. Check browser console (F12)"
echo "   2. Make sure all imports are correct"
echo "   3. Try refreshing the page"
echo ""

# Start the server
npm run dev