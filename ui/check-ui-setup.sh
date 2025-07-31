#!/bin/bash

# JoyAgent UI Diagnostic Script
# This script checks if the frontend is properly configured for document analysis

echo "🔍 JoyAgent UI Diagnostic Check"
echo "=================================="
echo ""

# Check current directory
echo "📁 Current directory: $(pwd)"
if [ ! -f "package.json" ]; then
    echo "❌ Not in UI directory. Please run from joyagent-jdgenie-main/ui/"
    exit 1
fi
echo "✅ In correct UI directory"
echo ""

# Check Node.js version
echo "🟢 Node.js version: $(node --version)"
echo "📦 npm version: $(npm --version)"
echo ""

# Check if DocumentAnalyzer component exists
echo "🔍 Checking DocumentAnalyzer component..."
if [ -f "src/components/DocumentAnalyzer.tsx" ]; then
    echo "✅ DocumentAnalyzer.tsx found"
    
    # Check if component is properly exported
    if grep -q "export default DocumentAnalyzer" "src/components/DocumentAnalyzer.tsx"; then
        echo "✅ Component is properly exported"
    else
        echo "⚠️ Component export might have issues"
    fi
    
    # Check if component is in index.ts
    if grep -q "DocumentAnalyzer" "src/components/index.ts"; then
        echo "✅ Component is exported from index.ts"
    else
        echo "❌ Component is NOT exported from index.ts"
    fi
else
    echo "❌ DocumentAnalyzer.tsx NOT found"
fi
echo ""

# Check Documents page
echo "🔍 Checking Documents page..."
if [ -f "src/pages/Documents/index.tsx" ]; then
    echo "✅ Documents page found"
    
    if grep -q "DocumentAnalyzer" "src/pages/Documents/index.tsx"; then
        echo "✅ Documents page imports DocumentAnalyzer"
    else
        echo "❌ Documents page does NOT import DocumentAnalyzer"
    fi
else
    echo "❌ Documents page NOT found"
fi
echo ""

# Check router configuration
echo "🔍 Checking router configuration..."
if [ -f "src/router/index.tsx" ]; then
    echo "✅ Router file found"
    
    if grep -q "/documents" "src/router/index.tsx"; then
        echo "✅ Documents route is configured"
    else
        echo "❌ Documents route is NOT configured"
    fi
else
    echo "❌ Router file NOT found"
fi
echo ""

# Check types
echo "🔍 Checking TypeScript types..."
if [ -f "src/types/document.ts" ]; then
    echo "✅ Document types found"
else
    echo "❌ Document types NOT found"
fi
echo ""

# Check dependencies
echo "🔍 Checking key dependencies in package.json..."
required_deps=("antd" "react" "react-dom" "typescript" "vite")
for dep in "${required_deps[@]}"; do
    if grep -q "\"$dep\"" "package.json"; then
        echo "✅ $dep found in package.json"
    else
        echo "❌ $dep NOT found in package.json"
    fi
done
echo ""

# Check if node_modules exists
echo "🔍 Checking installation..."
if [ -d "node_modules" ]; then
    echo "✅ node_modules directory exists"
    
    # Check if key packages are installed
    key_packages=("antd" "react" "typescript")
    for pkg in "${key_packages[@]}"; do
        if [ -d "node_modules/$pkg" ]; then
            echo "✅ $pkg is installed"
        else
            echo "❌ $pkg is NOT installed"
        fi
    done
else
    echo "❌ node_modules directory NOT found - need to run 'npm install'"
fi
echo ""

# Check ports
echo "🔍 Checking port availability..."
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️ Port 3000 is currently in use"
    echo "   Process: $(lsof -ti:3000 | xargs ps -p | tail -n +2)"
else
    echo "✅ Port 3000 is available"
fi
echo ""

# Summary and recommendations
echo "📋 Summary and Recommendations:"
echo "================================"

if [ -f "src/components/DocumentAnalyzer.tsx" ] && [ -f "src/pages/Documents/index.tsx" ]; then
    echo "✅ Core files are present"
    
    if [ -d "node_modules" ]; then
        echo "✅ Dependencies are installed"
        echo ""
        echo "🚀 Ready to start! Run one of these commands:"
        echo "   ./start-ui-simple.sh"
        echo "   npm run dev"
        echo ""
        echo "📊 Document Analysis will be at: http://localhost:3000/documents"
    else
        echo "❌ Need to install dependencies first:"
        echo "   npm install"
    fi
else
    echo "❌ Missing core files. Please ensure:"
    echo "   1. DocumentAnalyzer.tsx exists in src/components/"
    echo "   2. Documents page exists in src/pages/Documents/"
    echo "   3. Component is properly exported"
fi

echo ""
echo "🔧 If you're still having issues:"
echo "   1. Clear cache: rm -rf node_modules dist .vite"
echo "   2. Reinstall: npm install"
echo "   3. Check browser console for errors"
echo "   4. Ensure backend service is running on port 8188"