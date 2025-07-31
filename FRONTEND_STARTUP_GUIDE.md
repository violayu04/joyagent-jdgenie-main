# 🚀 JoyAgent Frontend Startup Guide

## Quick Start Steps

### 1. Check Your Setup
```bash
cd /Users/yuhaiyan/Desktop/joyagent-jdgenie-main/ui
./check-ui-setup.sh
```

### 2. Start the Frontend
```bash
# Simple startup
./start-ui-simple.sh

# OR manual startup
npm install  # if dependencies aren't installed
npm run dev
```

### 3. Access the Application
- **Home Page**: http://localhost:3000
- **Document Analysis**: http://localhost:3000/documents

## Troubleshooting Common Issues

### Issue 1: Component Not Loading
**Symptoms**: Document Analysis page shows errors or doesn't load

**Solutions**:
```bash
# Clear cache and restart
rm -rf node_modules dist .vite
npm install
npm run dev
```

### Issue 2: Port Already in Use
**Symptoms**: Error "Port 3000 is already in use"

**Solutions**:
```bash
# Kill existing process
lsof -ti:3000 | xargs kill -9
# Then restart
npm run dev
```

### Issue 3: API Connection Failed
**Symptoms**: "Cannot connect to document analysis service"

**Solutions**:
1. Start the backend document service:
   ```bash
   cd ../genie-client
   python server.py
   ```

2. Verify service is running:
   ```bash
   curl http://localhost:8188/health
   ```

### Issue 4: TypeScript Errors
**Symptoms**: Build fails with TypeScript errors

**Solutions**:
```bash
# Check TypeScript compilation
npx tsc --noEmit --skipLibCheck

# If errors persist, skip lib check temporarily
npm run dev
```

## Manual Step-by-Step Process

If the scripts don't work, follow these manual steps:

### Step 1: Navigate to UI Directory
```bash
cd /Users/yuhaiyan/Desktop/joyagent-jdgenie-main/ui
```

### Step 2: Install Dependencies
```bash
npm install
```

### Step 3: Start Development Server
```bash
npm run dev
```

### Step 4: Open Browser
Navigate to: http://localhost:3000/documents

## Expected File Structure

Your UI directory should have:
```
ui/
├── src/
│   ├── components/
│   │   ├── DocumentAnalyzer.tsx ✅
│   │   └── index.ts ✅
│   ├── pages/
│   │   └── Documents/
│   │       └── index.tsx ✅
│   ├── types/
│   │   └── document.ts ✅
│   └── router/
│       └── index.tsx ✅
├── package.json ✅
└── vite.config.ts ✅
```

## Testing the Document Analysis

Once the frontend is running:

1. Go to http://localhost:3000/documents
2. You should see:
   - File upload area with drag & drop
   - Analysis type selector (General, Financial, Compliance, Risk)
   - Query input text area
   - Analyze button

3. If you see a warning about service connection, that's normal - it means the backend document service (port 8188) isn't running yet.

## Next Steps

After the frontend is working:

1. **Start Backend Services**:
   ```bash
   # From project root
   ./start_genie_with_documents.sh
   ```

2. **Test Full Integration**:
   ```bash
   ./test_integration.sh
   ```

3. **Upload a Test Document** and try analysis

## Debug Information

If you're still having issues, check:

1. **Browser Console**: F12 → Console tab for JavaScript errors
2. **Network Tab**: F12 → Network tab to see failed API requests
3. **Service Status**: 
   - Frontend: http://localhost:3000 
   - Document API: http://localhost:8188/health
   - Backend API: http://localhost:8080/health

## Contact/Support

If none of these solutions work:
1. Run the diagnostic script: `./check-ui-setup.sh`
2. Check the browser console for specific error messages
3. Verify all files are in the correct locations
4. Try a fresh npm install: `rm -rf node_modules && npm install`