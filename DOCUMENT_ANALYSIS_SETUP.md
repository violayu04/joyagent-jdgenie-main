# JoyAgent Document Analysis Integration Guide

## Overview

This guide explains how to integrate the new document analysis functionality into your JoyAgent project. The system enables secure document upload and LLM-powered analysis specifically designed for banking environments.

## Architecture

The document analysis system consists of three main components:

1. **Python FastAPI Service** (`genie-client`) - Handles document processing and Qwen LLM integration
2. **Java Spring Boot Backend** (`genie-backend`) - Provides document analyzer tool integration
3. **React TypeScript Frontend** (`ui`) - User interface for document upload and analysis

## Prerequisites

Before setting up the document analysis feature, ensure you have:

- Python 3.10+ installed
- Java 11+ installed
- Node.js 16+ installed
- DashScope API key from Alibaba Cloud
- Network access to DashScope API (or configure internal proxy)

## Installation Steps

### 1. Backend Python Service Setup

Navigate to the genie-client directory and install dependencies:

```bash
cd genie-client

# Install Python dependencies
pip install -e .

# Or using uv (recommended)
uv sync
```

### 2. Configure Environment Variables

Copy the example environment file and configure it:

```bash
cp ../.env.example .env
```

Edit `.env` and set your DashScope API key:

```env
DASHSCOPE_API_KEY=your_actual_dashscope_api_key_here
```

### 3. Frontend Dependencies

Navigate to the UI directory and ensure Ant Design components are available:

```bash
cd ../ui
npm install  # or pnpm install
```

### 4. Java Backend Integration

The new `DocumentAnalyzerTool.java` has been added to your existing tools. No additional dependencies are required as it uses the existing OkHttp client.

## Configuration

### Document Processing Settings

In `genie-client/app/config.py`, you can adjust:

- `max_file_size_mb`: Maximum file size (default: 50MB)
- `supported_file_types`: List of allowed file extensions
- `max_content_length`: Maximum content length for LLM processing
- `qwen_model`: Qwen model to use (default: qwen-turbo)

### Security Settings for Banking Environment

The system includes several security features appropriate for banking:

1. **File Type Validation**: Strict whitelist of allowed file types
2. **Size Limits**: Configurable file size restrictions
3. **Content Sanitization**: Safe filename handling and content extraction
4. **No External Dependencies**: Uses local document processing (no external APIs for file processing)
5. **Audit Logging**: Comprehensive logging for compliance

## Usage

### 1. Start the Services

Start all three services in this order:

```bash
# 1. Start the Python document analysis service
cd genie-client
python server.py
# Service runs on http://localhost:8188

# 2. Start the Java backend (in new terminal)
cd ../genie-backend
./start.sh
# Service runs on http://localhost:8080

# 3. Start the React frontend (in new terminal)
cd ../ui
npm run dev
# Service runs on http://localhost:3000
```

### 2. Using the Document Analyzer Tool

#### Option A: Through the UI Component

The React component `DocumentAnalyzer` can be integrated into your existing UI:

```typescript
import DocumentAnalyzer from './components/DocumentAnalyzer';

// Use in your component
<DocumentAnalyzer 
  sessionId={currentSessionId}
  userId={currentUserId}
  onAnalysisComplete={(results) => {
    console.log('Analysis completed:', results);
  }}
/>
```

#### Option B: Through the Agent Tool

The Java tool can be used by the LLM agent directly:

```
User: "Please analyze the uploaded financial report and identify key metrics."

Agent uses: document_analyzer tool with parameters:
{
  "command": "analyze",
  "query": "Identify key financial metrics and performance indicators",
  "files": ["financial_report_q3.pdf"],
  "analysis_type": "financial"
}
```

#### Option C: Direct API Usage

You can also call the API directly:

```bash
curl -X POST "http://localhost:8188/v1/document/analyze" \
  -F "query=Summarize this document" \
  -F "session_id=12345" \
  -F "analysis_type=general" \
  -F "files=@/path/to/document.pdf"
```

## API Endpoints

### Document Analysis Service (Port 8188)

- `GET /health` - Health check with document analysis status
- `GET /v1/document/supported-formats` - Get supported file formats
- `POST /v1/document/analyze` - Analyze uploaded documents
- `POST /v1/document/analyze-batch` - Batch analysis
- `POST /v1/document/clear-cache` - Clear processing cache

### Example API Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total_files": 1,
    "successful_analyses": 1,
    "results": [
      {
        "success": true,
        "analysis": "This financial report shows...",
        "metadata": {
          "filename": "report.pdf",
          "file_type": ".pdf",
          "file_size": 2048576,
          "word_count": 1250,
          "page_count": 15
        },
        "timestamp": "2025-01-31T10:30:00Z"
      }
    ],
    "query": "Analyze financial metrics",
    "session_id": "session_123"
  }
}
```

## Supported File Types

The system supports the following document types:

- **PDF** (.pdf) - Requires PyPDF2
- **Word Documents** (.docx, .doc) - Requires python-docx
- **Text Files** (.txt, .md) - Native support
- **CSV Files** (.csv) - Requires pandas
- **JSON Files** (.json) - Native support
- **XML Files** (.xml) - Native support
- **HTML Files** (.html) - Native support

## Banking-Specific Features

### 1. Analysis Types

The system provides specialized analysis types for banking:

- **General**: Document summarization, key information extraction
- **Financial**: Financial metrics identification, performance analysis
- **Compliance**: Regulatory compliance checking, policy adherence
- **Risk**: Risk factor identification, risk assessment

### 2. Security Considerations

- All file processing is done locally (no external file processing APIs)
- Configurable file type restrictions
- Size limits to prevent resource exhaustion
- Secure filename handling to prevent path traversal
- Content sanitization before LLM processing

### 3. Audit and Compliance

- Comprehensive logging of all document processing activities
- Session tracking for audit trails
- Metadata preservation for compliance reporting
- Error tracking and reporting

## Troubleshooting

### Common Issues

1. **DashScope API Key Error**
   - Verify your API key is correctly set in `.env`
   - Check network connectivity to DashScope
   - Ensure sufficient API quota

2. **File Processing Errors**
   - Install required dependencies: `pip install PyPDF2 python-docx pandas`
   - Check file permissions and size limits
   - Verify file is not corrupted

3. **Service Communication Issues**
   - Ensure all services are running on correct ports
   - Check firewall settings
   - Verify service URLs in configuration

4. **Memory Issues with Large Files**
   - Adjust `MAX_CONTENT_LENGTH` in configuration
   - Consider implementing file chunking for very large documents
   - Monitor memory usage during processing

### Debug Mode

Enable debug logging by setting environment variables:

```bash
export LOG_LEVEL=DEBUG
export ENABLE_AUDIT_LOGGING=true
```

## Integration with Existing Workflow

### 1. File Upload Integration

If you already have file upload functionality, you can integrate with the new system by:

1. Using the existing `FileTool` to upload documents
2. Then calling `DocumentAnalyzerTool` to analyze uploaded files
3. The system will automatically find files by name from the context

### 2. Agent Workflow Integration

The document analyzer integrates seamlessly with your existing agent workflow:

```java
// In your agent planning/execution logic
if (userQuery.contains("analyze") && contextHasFiles()) {
    // Agent automatically suggests using document_analyzer tool
    toolToUse = "document_analyzer";
    parameters.put("command", "analyze");
    parameters.put("query", extractAnalysisQuery(userQuery));
    parameters.put("files", getAvailableFileNames());
}
```

## Performance Considerations

1. **File Processing**: Large documents (>10MB) may take longer to process
2. **LLM Analysis**: Response time depends on content length and complexity
3. **Concurrent Users**: Consider implementing request queuing for high load
4. **Caching**: The system caches processed documents to improve performance

## Future Enhancements

Potential improvements for banking environments:

1. **Document Classification**: Automatic categorization of uploaded documents
2. **Compliance Templates**: Pre-built analysis templates for common banking documents
3. **Multi-language Support**: Analysis of documents in multiple languages
4. **Integration with Document Management Systems**: Direct integration with bank's DMS
5. **Advanced Security**: Document encryption, digital signatures validation

## Support

For issues or questions regarding the document analysis feature:

1. Check the logs in `genie-client/Logs/` directory
2. Verify all services are running and accessible
3. Test with simple documents first before complex files
4. Ensure your DashScope API key has sufficient quota

The system is designed to be robust and secure for banking environments while providing powerful document analysis capabilities through LLM integration.
