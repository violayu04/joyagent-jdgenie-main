#!/bin/bash

# Simple Frontend Startup Script for JoyAgent Document Analysis
# Run this from the ui/ directory

echo "🚀 Starting JoyAgent UI with Document Analysis..."
echo ""

# Check if we're in the right directory
if [ ! -f "package.json" ]; then
    echo "❌ Error: Please run this script from the ui/ directory"
    echo "Current directory: $(pwd)"
    echo "Expected files: package.json, src/components/DocumentAnalyzer.tsx"
    exit 1
fi

echo "✅ Directory check passed"

# Check if DocumentAnalyzer component exists
if [ ! -f "src/components/DocumentAnalyzer.tsx" ]; then
    echo "❌ Error: DocumentAnalyzer.tsx component not found"
    echo "Please ensure the component exists at src/components/DocumentAnalyzer.tsx"
    exit 1
fi

echo "✅ DocumentAnalyzer component found"

# Check if port 3000 is already in use
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️ Warning: Port 3000 is already in use"
    echo "Stopping existing process..."
    kill -9 $(lsof -ti:3000) 2>/dev/null || true
    sleep 2
fi

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
    if [ $? -ne 0 ]; then
        echo "❌ Failed to install dependencies"
        exit 1
    fi
else
    echo "✅ Dependencies already installed"
fi

# Clear any cache
echo "🧹 Clearing cache..."
rm -rf dist .vite node_modules/.vite 2>/dev/null || true

echo ""
echo "🌐 Starting development server..."
echo "📊 Document Analysis will be available at: http://localhost:3000/documents"
echo "🏠 Home page will be available at: http://localhost:3000"
echo ""
echo "💡 If the Document Analysis page doesn't load:"
echo "   1. Make sure the backend document service is running on port 8188"
echo "   2. Check browser console for any errors"
echo "   3. Try refreshing the page"
echo ""
echo "🛑 Press Ctrl+C to stop the server"
echo ""

# Start the development server
npm run dev