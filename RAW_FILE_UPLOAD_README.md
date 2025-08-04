# JoyAgent 原始文档上传功能实现

## 概述

本实现为 JoyAgent 项目添加了"上传文档功能"，实现了**后端作为纯粹管道，将原始文档内容直接发送给 LLM**的核心需求。后端不进行任何文本提取、内容分析或预处理，完全由 LLM 负责文档的解读和分析。

## 🎯 实现目标

✅ **后端纯管道**：接收文件 → 读取原始字节 → Base64编码 → 直接发送给LLM  
✅ **跳过所有处理**：不进行PDF解析、DOCX文本提取、图片OCR等任何预处理  
✅ **LLM全权负责**：文档解读、内容分析、格式识别完全由LLM处理  
✅ **支持多种格式**：PDF、Word、Excel、图片、文本等格式  
✅ **高性能处理**：支持最大50MB文件上传

## 📁 新增文件

### 1. DTO 类
- `RawFileRequest.java` - 原始文件请求数据传输对象
- 包含：requestId、fileName、description、rawBytes（base64编码）

### 2. Service 层
- `IRawFileService.java` - 原始文件处理服务接口
- `RawFileService.java` - 核心实现类，负责直接发送给LLM

### 3. Controller 层  
- `RawFileController.java` - REST API控制器
- 提供 `/api/raw-file/upload` 端点

### 4. 配置文件
- `raw-file-config.yml` - 原始文件上传相关配置

## 🔧 修改文件

### 1. FileTool.java
- 添加 `upload_raw` 命令支持
- 新增 `uploadRawFile()` 方法
- 更新参数描述以包含 `rawBytes`

### 2. application.yml  
- 添加文件上传配置（50MB限制）
- 更新 file_tool 工具描述和参数

## 🚀 使用方式

### API 调用
```bash
curl -X POST http://localhost:8080/api/raw-file/upload \
  -F "file=@document.pdf" \
  -F "description=测试文档"
```

### 工具调用（在智能体中）
```json
{
  "function_name": "file_tool",
  "command": "upload_raw", 
  "filename": "document.pdf",
  "description": "用户上传的PDF文档",
  "rawBytes": "JVBERi0xLjQKMSAwIG9iago8PC9UeXBlIC9DYXRhbG9n..."
}
```

## 🔍 核心实现逻辑

### 1. 文件接收
```java
// 读取原始字节
byte[] fileBytes = file.getBytes();

// Base64编码
String base64Content = Base64.getEncoder().encodeToString(fileBytes);
```

### 2. 直接发送给LLM
```java
// 构建LLM请求，包含原始文件内容
Map<String, Object> fileContent = new HashMap<>();
fileContent.put("type", "document");
fileContent.put("document", Map.of(
    "data", rawFileRequest.getRawBytes(), // 原始base64内容
    "filename", rawFileRequest.getFileName(),
    "description", rawFileRequest.getDescription()
));
```

### 3. 关键特点
- ❌ **不调用** DocumentAnalyzerTool
- ❌ **不调用** Code Interpreter 进行文本提取  
- ❌ **不进行** PDF解析或OCR识别
- ✅ **直接发送** 原始字节给LLM API

## 📊 流程图

```
用户上传文件
    ↓
读取原始字节内容
    ↓  
Base64编码
    ↓
构建LLM请求（包含原始内容）
    ↓
直接发送给LLM API
    ↓
返回LLM分析结果
```

## 🛠️ 部署步骤

### 1. 复制文件
将以下新文件复制到对应目录：
- `RawFileRequest.java` → `src/main/java/com/jd/genie/agent/dto/`
- `IRawFileService.java` → `src/main/java/com/jd/genie/service/`
- `RawFileService.java` → `src/main/java/com/jd/genie/service/`
- `RawFileController.java` → `src/main/java/com/jd/genie/controller/`

### 2. 替换文件
- 备份原有 `FileTool.java`
- 用新版本替换 `FileTool.java`

### 3. 更新配置
将 `raw-file-config.yml` 中的配置合并到 `application.yml`

### 4. 重启服务
```bash
./start.sh
```

## 🧪 验证测试

### 1. 功能测试
```bash
# 测试PDF上传
curl -X POST http://localhost:8080/api/raw-file/upload \
  -F "file=@test.pdf" \
  -F "description=测试PDF文档"

# 测试Word文档
curl -X POST http://localhost:8080/api/raw-file/upload \
  -F "file=@test.docx" \
  -F "description=测试Word文档"
```

### 2. 验证日志
检查日志中是否出现：
```
[INFO] 开始处理原始文件: test.pdf
[INFO] 发送原始文件到LLM: test.pdf -> https://dashscope.aliyuncs.com/...
[INFO] 原始文件已成功发送给LLM并获得响应: test.pdf
```

### 3. 确认无文本提取
确保日志中**没有**出现：
- "文档解析"
- "文本提取" 
- "OCR识别"
- "DocumentAnalyzerTool"

## 📈 性能特点

- **文件大小限制**：50MB
- **支持格式**：PDF、DOC/DOCX、XLS/XLSX、TXT、PNG/JPG/GIF
- **处理方式**：零预处理，直接传输
- **响应时间**：取决于LLM API响应时间
- **内存使用**：Base64编码会增加约33%内存占用

## 🔒 安全考虑

1. **文件大小限制**：防止过大文件影响系统性能
2. **文件类型检查**：仅允许指定格式文件上传
3. **请求ID追踪**：每个请求都有唯一ID便于调试
4. **错误处理**：完善的异常处理和用户反馈

## 🐛 故障排除

### 常见问题

1. **文件上传失败**
   - 检查文件大小是否超过50MB限制
   - 确认文件格式是否支持

2. **LLM请求失败**  
   - 验证API密钥配置
   - 检查网络连接
   - 确认LLM服务可用性

3. **Base64编码错误**
   - 检查文件是否损坏
   - 验证文件读取过程

### 调试步骤

1. 启用DEBUG日志级别：
```yaml
logging:
  level:
    com.jd.genie.service.RawFileService: DEBUG
```

2. 检查关键日志：
```
开始处理原始文件: [filename]
发送原始文件到LLM: [filename] -> [llm_url]  
LLM原始响应: [response]
原始文件已成功发送给LLM并获得响应: [filename]
```

## 📋 API 文档

### POST /api/raw-file/upload

**描述**：上传原始文件并直接发送给LLM处理

**请求参数**：
- `file`：上传的文件（multipart/form-data）
- `description`：文件描述（可选，默认："用户上传的文档"）

**响应格式**：
```json
{
  "success": true,
  "message": "文件上传成功，已发送给LLM处理",
  "llmResponse": "LLM的分析结果...",
  "fileName": "document.pdf"
}
```

**错误响应**：
```json  
{
  "success": false,
  "message": "错误描述",
  "llmResponse": null,
  "fileName": "document.pdf"
}
```

## 📝 更新日志

### v1.0.0 (初始版本)
- ✅ 实现原始文档直接上传给LLM功能
- ✅ 支持多种文件格式（PDF、Word、Excel、图片等）
- ✅ 完整的错误处理和日志记录
- ✅ RESTful API接口
- ✅ 工具集成支持

---

## 🎉 总结

本实现成功为 JoyAgent 项目添加了"上传文档功能"，完全按照需求实现了：

1. **后端纯管道**：不进行任何文本提取或内容分析
2. **直接发送LLM**：原始文件内容通过Base64编码直接传递
3. **LLM全权处理**：文档解读和分析完全由LLM负责
4. **高性能**：支持大文件，响应迅速
5. **易于使用**：提供REST API和工具调用两种方式

现在您的 JoyAgent 项目具备了真正的"原始文档上传"能力！🚀
