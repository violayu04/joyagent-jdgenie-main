# OCR-Enhanced Table Extraction

This document describes the OCR (Optical Character Recognition) enhancements added to improve table extraction from images and PDF documents.

## Features

### 🔍 OCR Capabilities
- **Multi-format Support**: Extract text from images (JPEG, PNG, GIF) and PDF files
- **Table Detection**: Automatically detect and extract table structures
- **Multi-language**: Support for English and Chinese text recognition
- **Confidence Scoring**: Provides accuracy confidence for extracted content

### 📊 Table Extraction Enhancements
- **Intelligent Chunking**: Preserves table structure during text chunking
- **HTML Generation**: Converts OCR-detected tables to HTML format
- **Metadata Preservation**: Maintains table dimensions and structure information
- **Fallback Strategy**: Falls back to standard text processing if OCR fails

## Installation Requirements

### Tesseract OCR
The system requires Tesseract OCR to be installed on the system:

#### macOS (using Homebrew):
```bash
brew install tesseract
brew install tesseract-lang  # For additional language support
```

#### Ubuntu/Debian:
```bash
sudo apt-get update
sudo apt-get install tesseract-ocr
sudo apt-get install tesseract-ocr-chi-sim  # For Chinese support
```

#### Training Data Location
Tesseract looks for training data in these locations:
- `/usr/local/share/tessdata` (macOS Homebrew)
- `/usr/share/tessdata` (Linux)
- `/opt/homebrew/share/tessdata` (macOS Apple Silicon)

## API Endpoints

### 1. OCR Status Check
```http
GET /api/ocr-test/status
```

**Response:**
```json
{
  "ocrServiceAvailable": true,
  "supportedFormats": ["image/jpeg", "image/png", "image/gif", "application/pdf"],
  "features": ["Text extraction", "Table detection", "Multi-language support", "Confidence scoring"]
}
```

### 2. OCR Text Extraction
```http
POST /api/ocr-test/extract
Content-Type: multipart/form-data

file: [image or PDF file]
```

**Response:**
```json
{
  "success": true,
  "extractedText": "Extracted text content...",
  "confidence": 0.95,
  "textLength": 1523,
  "tablesDetected": 2,
  "tables": [
    {
      "content": "Table data...",
      "rows": 5,
      "columns": 3,
      "confidence": 0.89,
      "startLine": 10,
      "endLine": 15
    }
  ]
}
```

### 3. OCR-Enhanced Chunking
```http
POST /api/ocr-test/chunk
Content-Type: multipart/form-data

file: [image or PDF file]
```

**Response:**
```json
{
  "success": true,
  "totalChunks": 8,
  "tableChunks": 2,
  "textChunks": 6,
  "chunkTypeStats": {
    "TABLE": 2,
    "TEXT": 6
  },
  "chunkDetails": [
    {
      "index": 0,
      "type": "TABLE",
      "tokenCount": 245,
      "contentPreview": "Product | Price | Quantity...",
      "tableRows": 5,
      "tableCols": 3,
      "htmlContent": "<table><tr><th>Product</th><th>Price</th>...</table>"
    }
  ]
}
```

## Usage in Knowledge Base

The OCR functionality is automatically integrated into the document processing pipeline:

1. **Document Upload**: When uploading images or PDFs to the knowledge base
2. **Content Detection**: System automatically detects if OCR processing is beneficial
3. **Enhanced Extraction**: Tables are preserved as structured chunks
4. **Vector Storage**: Both text content and table structure are stored for retrieval

## Configuration

### Vector Configuration (application.yml)
```yaml
vector:
  chunking:
    chunk-size: 2000
    chunk-overlap: 200
    strategy: semantic
    enable-semantic-chunking: true
    max-table-chunk-size: 4000  # Larger chunks for tables
```

### OCR Settings
The OCR service can be configured through environment variables or application properties:

```yaml
ocr:
  tesseract:
    datapath: "/usr/local/share/tessdata"
    language: "eng+chi_sim"
    oem: 1  # LSTM OCR Engine Mode
    psm: 6  # Page Segmentation Mode
```

## Advanced Features

### 1. Table Structure Preservation
- Maintains original table layout and relationships
- Converts to HTML format for better rendering
- Preserves cell alignment and headers

### 2. Multi-language Support
- English and Chinese text recognition
- Configurable language models
- Character whitelist for improved accuracy

### 3. Quality Assessment
- Confidence scoring for extracted text
- Table detection reliability metrics
- Automatic fallback for low-confidence results

### 4. Performance Optimization
- Chunked processing for large documents
- Efficient memory usage for PDF conversion
- Parallel processing for multi-page documents

## Best Practices

### 1. Input Quality
- Use high-resolution images (300 DPI or higher)
- Ensure good contrast between text and background
- Avoid skewed or rotated documents when possible

### 2. Table Formats
- Works best with clearly defined table borders
- Supports various table layouts (grid, list, mixed)
- Handles both typed and handwritten content

### 3. Performance Considerations
- Large PDFs may take longer to process
- Consider splitting very large documents
- Monitor memory usage for batch processing

## Troubleshooting

### Common Issues

1. **Tesseract Not Found**
   - Ensure Tesseract is installed and in PATH
   - Check training data location
   - Verify language packs are installed

2. **Poor OCR Quality**
   - Check input image quality
   - Verify language settings
   - Consider image preprocessing

3. **Table Detection Issues**
   - Ensure clear table structure
   - Check for proper cell alignment
   - Verify adequate spacing between cells

### Debug Mode
Enable debug logging to troubleshoot OCR issues:

```yaml
logging:
  level:
    com.jd.genie.service.OcrService: DEBUG
    com.jd.genie.service.HtmlTableAwareChunkingService: DEBUG
```

## Integration Examples

### 1. Upload PDF with Tables
```bash
curl -X POST http://localhost:8080/api/ocr-test/extract \
  -F "file=@financial_report.pdf" \
  -H "Content-Type: multipart/form-data"
```

### 2. Process Invoice Image
```bash
curl -X POST http://localhost:8080/api/ocr-test/chunk \
  -F "file=@invoice_scan.jpg" \
  -H "Content-Type: multipart/form-data"
```

### 3. Batch Process Documents
Process multiple documents through the knowledge base API with automatic OCR detection and table extraction.

## Future Enhancements

- **Form Recognition**: Enhanced support for structured forms
- **Handwriting Recognition**: Improved handwritten text extraction  
- **Layout Analysis**: Better understanding of document structure
- **Custom Training**: Support for domain-specific OCR models