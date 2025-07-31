# 📄 JoyAgent Document Analysis System

## Overview

The JoyAgent Document Analysis System is a comprehensive solution for secure document upload, processing, and AI-powered analysis designed specifically for banking environments. It integrates seamlessly with your existing JoyAgent infrastructure to provide intelligent document insights using Qwen LLM.

## 🚀 Key Features

### 📋 Document Processing
- **Multi-format Support**: PDF, DOCX, DOC, TXT, CSV, JSON, MD, XML, HTML
- **Secure Upload**: File validation, size limits, and secure filename handling
- **Content Extraction**: Advanced text extraction with metadata preservation
- **Batch Processing**: Analyze multiple documents simultaneously

### 🤖 AI-Powered Analysis
- **Qwen LLM Integration**: Powered by Alibaba's Qwen through DashScope API
- **Banking-Specific Analysis**: Financial metrics, compliance checking, risk assessment
- **Contextual Understanding**: Considers document type and content structure
- **Configurable Prompts**: Specialized prompts for different analysis types

### 🏦 Banking Environment Features
- **Compliance Ready**: Audit logging and secure processing
- **No External File Processing**: All document processing done locally
- **Restricted Network Access**: Only LLM API calls when needed
- **Security First**: File type validation, size limits, content sanitization

## ⚡ Quick Start

### 1. Setup Environment
```bash
# Copy environment template
cp .env.example .env

# Edit .env and set your DashScope API key
nano .env  # Set DASHSCOPE_API_KEY=your_actual_key_here
```

### 2. Install Dependencies and Start
```bash
# Install all dependencies and start services
./start_genie_with_documents.sh --install-deps

# Or start without installing (if already installed)
./start_genie_with_documents.sh
```

### 3. Access the System
- **Frontend**: http://localhost:3000
- **Document API**: http://localhost:8188
- **Backend API**: http://localhost:8080

### 4. Test Integration
```bash
# Run integration tests
./test_integration.sh
```

## 📚 Usage Examples

### Via React UI
1. Open http://localhost:3000
2. Navigate to Document Analysis section
3. Upload PDF, DOCX, or other supported files
4. Enter analysis query (e.g., "Summarize financial metrics")
5. Select analysis type (General, Financial, Compliance, Risk)
6. Click "Analyze" and view results

### Via Agent Tool
```
User: "Please analyze the uploaded quarterly report and identify key financial indicators."

Agent automatically uses:
{
  "tool": "document_analyzer",
  "parameters": {
    "command": "analyze",
    "query": "Identify key financial indicators and performance metrics",
    "files": ["quarterly_report_q3.pdf"],
    "analysis_type": "financial"
  }
}
```

### Via API
```bash
# Upload and analyze document
curl -X POST "http://localhost:8188/v1/document/analyze" \
  -F "query=Summarize this financial report" \
  -F "session_id=banking_session_123" \
  -F "analysis_type=financial" \
  -F "files=@report.pdf"

# Get supported formats
curl "http://localhost:8188/v1/document/supported-formats"
```

## 🎯 Analysis Types

### General Analysis
- Document summarization
- Key information extraction
- Content structure analysis
- Topic identification

### Financial Analysis
- Revenue and profit metrics
- Performance indicators (KPIs)
- Financial ratios analysis
- Growth trends identification
- Cost structure analysis

### Compliance Analysis
- Regulatory requirement checking
- Policy adherence verification
- Compliance gap identification
- Audit trail analysis

### Risk Analysis
- Risk factor identification
- Risk level assessment
- Mitigation strategy analysis
- Vulnerability detection

## 🔧 Configuration

### Environment Variables (.env)
```env
# Required
DASHSCOPE_API_KEY=your_dashscope_api_key_here

# Optional
UPLOAD_DIRECTORY=uploads
MAX_FILE_SIZE_MB=50
MAX_CONTENT_LENGTH=8000
QWEN_MODEL=qwen-turbo
ANALYSIS_TIMEOUT=60
```

### Supported File Types

| Format | Extension | Library Required |
|--------|-----------|------------------|
| PDF | .pdf | PyPDF2 |
| Word | .docx, .doc | python-docx |
| Text | .txt, .md | Built-in |
| CSV | .csv | pandas |
| JSON | .json | Built-in |
| XML | .xml | Built-in |
| HTML | .html | Built-in |

## 🛡️ Security Features

- **File Size Limit**: 50MB (configurable)
- **File Type Validation**: Strict whitelist
- **Content Sanitization**: Safe filename handling
- **Local Processing**: No external file processing APIs
- **Audit Logging**: Comprehensive activity logs
- **Session Tracking**: User and session-based access control

## 🔗 API Reference

### POST /v1/document/analyze
Analyze uploaded documents with AI.

**Parameters:**
- `query` (string): Analysis question
- `session_id` (string): Session identifier
- `analysis_type` (string): general|financial|compliance|risk
- `files` (file[]): Documents to analyze
- `user_id` (string, optional): User identifier

**Response:**
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
        "analysis": "Based on the financial report analysis...",
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
    "query": "Summarize financial metrics",
    "session_id": "banking_session_123"
  }
}
```

### GET /v1/document/supported-formats
Get list of supported file formats.

## 🔧 Troubleshooting

### Common Issues

1. **Service Not Starting**
   ```bash
   # Check if ports are in use
   lsof -i :8188 -i :8080 -i :3000
   
   # Check service logs
   tail -f logs/document-service.log
   ```

2. **API Key Issues**
   ```bash
   # Verify API key is set
   echo $DASHSCOPE_API_KEY
   ```

3. **File Processing Errors**
   ```bash
   # Check Python dependencies
   cd genie-client
   source .venv/bin/activate
   pip list | grep -E "PyPDF2|python-docx|pandas"
   ```

### Debug Mode

Enable detailed logging:
```bash
# In .env file
LOG_LEVEL=DEBUG
ENABLE_AUDIT_LOGGING=true

# Restart services
./stop_genie.sh
./start_genie_with_documents.sh
```

## 📊 Performance

### Processing Benchmarks

| File Type | Size | Processing Time | Memory Usage |
|-----------|------|----------------|-------------|
| PDF | 10MB | 5-15 seconds | 200-500MB |
| DOCX | 5MB | 2-8 seconds | 100-300MB |
| TXT | 1MB | 1-3 seconds | 50-100MB |
| CSV | 20MB | 3-10 seconds | 300-800MB |

## 🚀 Integration with Existing Workflow

The document analyzer integrates seamlessly with your existing JoyAgent workflow:

1. **File Upload**: Use existing `FileTool` to upload documents
2. **Analysis**: Call `DocumentAnalyzerTool` to analyze uploaded files
3. **Results**: System automatically finds files by name from context

## 📞 Support

For technical support:
1. **Documentation**: Check `DOCUMENT_ANALYSIS_SETUP.md` for detailed setup
2. **Logs**: Review service logs in `./logs/` directory
3. **Testing**: Run `./test_integration.sh` to diagnose issues
4. **Configuration**: Verify environment variables in `.env` file

---

**🏦 Designed for Banking Excellence** | **🔒 Security First** | **⚡ High Performance**

The JoyAgent Document Analysis System provides enterprise-grade document processing capabilities specifically tailored for banking environments, ensuring security, compliance, and performance at every step.