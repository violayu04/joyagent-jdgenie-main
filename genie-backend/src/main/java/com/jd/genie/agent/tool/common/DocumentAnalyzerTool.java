package com.jd.genie.agent.tool.common;

import com.alibaba.fastjson.JSON;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.dto.CodeInterpreterResponse;
import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Data
public class DocumentAnalyzerTool implements BaseTool {
    private AgentContext agentContext;

    @Override
    public String getName() {
        return "document_analyzer";
    }

    @Override
    public String getDescription() {
        return "Advanced document analysis tool for banking environments. " +
               "Supports PDF, DOCX, TXT, CSV, JSON, and MD files. " +
               "Analyzes financial documents for metrics, risk factors, compliance issues, and business insights using LLM analysis.";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> command = new HashMap<>();
        command.put("type", "string");
        command.put("description", "Analysis command: 'analyze' for document analysis, 'formats' for supported formats");
        command.put("enum", Arrays.asList("analyze", "formats"));

        Map<String, Object> query = new HashMap<>();
        query.put("type", "string");
        query.put("description", "Analysis query or question about the documents");

        Map<String, Object> files = new HashMap<>();
        files.put("type", "array");
        files.put("description", "Array of file names or file IDs to analyze");
        Map<String, Object> fileItems = new HashMap<>();
        fileItems.put("type", "string");
        files.put("items", fileItems);

        Map<String, Object> analysisType = new HashMap<>();
        analysisType.put("type", "string");
        analysisType.put("description", "Type of analysis: general, financial, compliance, risk");
        analysisType.put("enum", Arrays.asList("general", "financial", "compliance", "risk"));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("command", command);
        properties.put("query", query);
        properties.put("files", files);
        properties.put("analysis_type", analysisType);
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("command"));

        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = (Map<String, Object>) input;
            String command = (String) params.getOrDefault("command", "");
            
            switch (command) {
                case "analyze":
                    return analyzeDocuments(params);
                case "formats":
                    return getSupportedFormats();
                default:
                    return "Invalid command. Use 'analyze' or 'formats'";
            }
        } catch (Exception e) {
            log.error("{} document analyzer tool error", agentContext.getRequestId(), e);
            return "Error executing document analysis: " + e.getMessage();
        }
    }

    private String analyzeDocuments(Map<String, Object> params) {
        String query = (String) params.get("query");
        List<String> fileNames = (List<String>) params.getOrDefault("files", new ArrayList<>());
        String analysisType = (String) params.getOrDefault("analysis_type", "general");

        if (query == null || query.trim().isEmpty()) {
            return "Error: Analysis query is required";
        }

        if (fileNames.isEmpty()) {
            return "Error: No files specified for analysis";
        }

        // Get available files from context
        List<File> availableFiles = agentContext.getProductFiles();
        List<File> filesToAnalyze = new ArrayList<>();

        for (String fileName : fileNames) {
            File foundFile = availableFiles.stream()
                    .filter(f -> f.getFileName().equals(fileName))
                    .findFirst()
                    .orElse(null);
            
            if (foundFile != null) {
                filesToAnalyze.add(foundFile);
            } else {
                log.warn("{} File not found: {}", agentContext.getRequestId(), fileName);
            }
        }

        if (filesToAnalyze.isEmpty()) {
            return "Error: None of the specified files were found in the current context";
        }

        try {
            return performDocumentAnalysis(query, filesToAnalyze, analysisType);
        } catch (Exception e) {
            log.error("{} Error performing document analysis", agentContext.getRequestId(), e);
            return "Error performing document analysis: " + e.getMessage();
        }
    }

    private String performDocumentAnalysis(String query, List<File> files, String analysisType) throws IOException {
        GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);
        
        // Use the Python client endpoint
        String analysisUrl = "http://localhost:8188/v1/document/analyze";
        
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .build();

        try {
            // Build multipart request
            MultipartBody.Builder requestBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("query", query)
                    .addFormDataPart("session_id", agentContext.getSessionId())
                    .addFormDataPart("analysis_type", analysisType);

            // Add files to the request
            for (File file : files) {
                if (file.getOssUrl() != null && !file.getOssUrl().isEmpty()) {
                    // Download file content and add to request
                    String fileContent = downloadFileContent(file.getOssUrl());
                    if (fileContent != null) {
                        RequestBody fileBody = RequestBody.create(
                            fileContent.getBytes(),
                            MediaType.parse("application/octet-stream")
                        );
                        requestBuilder.addFormDataPart("files", file.getFileName(), fileBody);
                    }
                }
            }

            RequestBody requestBody = requestBuilder.build();
            Request request = new Request.Builder()
                    .url(analysisUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "multipart/form-data")
                    .build();

            log.info("{} Sending document analysis request for {} files", 
                    agentContext.getRequestId(), files.size());

            Response response = client.newCall(request).execute();
            
            if (!response.isSuccessful() || response.body() == null) {
                log.error("{} Document analysis request failed with status: {}", 
                         agentContext.getRequestId(), response.code());
                return "Document analysis service unavailable";
            }

            String responseBody = response.body().string();
            log.info("{} Document analysis response: {}", agentContext.getRequestId(), responseBody);

            // Parse response
            Map<String, Object> responseMap = JSON.parseObject(responseBody, Map.class);
            
            if (responseMap.get("code").equals(200)) {
                Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
                List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
                
                StringBuilder analysisResult = new StringBuilder();
                analysisResult.append("Document Analysis Results:\n\n");
                
                int successfulAnalyses = (Integer) data.get("successful_analyses");
                int totalFiles = (Integer) data.get("total_files");
                
                analysisResult.append(String.format("Successfully analyzed %d out of %d documents.\n\n", 
                                                   successfulAnalyses, totalFiles));

                for (Map<String, Object> result : results) {
                    Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
                    String filename = (String) metadata.get("filename");
                    Boolean success = (Boolean) result.get("success");
                    
                    analysisResult.append("📄 **").append(filename).append("**\n");
                    
                    if (success) {
                        String analysis = (String) result.get("analysis");
                        analysisResult.append(analysis).append("\n\n");
                    } else {
                        String error = (String) result.get("error");
                        analysisResult.append("❌ Analysis failed: ").append(error).append("\n\n");
                    }
                }

                // Notify frontend with analysis results
                notifyFrontend(analysisResult.toString(), files);
                
                return analysisResult.toString();
            } else {
                String errorMessage = (String) responseMap.get("message");
                return "Document analysis failed: " + errorMessage;
            }

        } catch (Exception e) {
            log.error("{} Error in document analysis request", agentContext.getRequestId(), e);
            return "Error communicating with document analysis service: " + e.getMessage();
        }
    }

    private String downloadFileContent(String url) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                log.error("{} Failed to download file from: {}", agentContext.getRequestId(), url);
                return null;
            }
        } catch (IOException e) {
            log.error("{} Error downloading file from: {}", agentContext.getRequestId(), url, e);
            return null;
        }
    }

    private String getSupportedFormats() {
        try {
            String formatsUrl = "http://localhost:8188/v1/document/supported-formats";
            
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(formatsUrl)
                    .get()
                    .build();

            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                Map<String, Object> responseMap = JSON.parseObject(responseBody, Map.class);
                
                if (responseMap.get("code").equals(200)) {
                    Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
                    Map<String, String> formats = (Map<String, String>) data.get("supported_formats");
                    Integer maxSizeMb = (Integer) data.get("max_file_size_mb");
                    
                    StringBuilder result = new StringBuilder();
                    result.append("Supported Document Formats:\n\n");
                    
                    for (Map.Entry<String, String> entry : formats.entrySet()) {
                        result.append("• ").append(entry.getKey().toUpperCase())
                              .append(" (").append(entry.getValue()).append(")\n");
                    }
                    
                    result.append("\nMaximum file size: ").append(maxSizeMb).append(" MB");
                    
                    return result.toString();
                } else {
                    return "Error retrieving supported formats";
                }
            } else {
                return "Document analysis service unavailable";
            }
        } catch (Exception e) {
            log.error("{} Error getting supported formats", agentContext.getRequestId(), e);
            return "Error retrieving supported formats: " + e.getMessage();
        }
    }

    private void notifyFrontend(String analysisResult, List<File> analyzedFiles) {
        try {
            // Build frontend notification
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("command", "文档分析结果");
            
            // Create file info for frontend
            List<CodeInterpreterResponse.FileInfo> fileInfo = new ArrayList<>();
            for (File file : analyzedFiles) {
                fileInfo.add(CodeInterpreterResponse.FileInfo.builder()
                        .fileName(file.getFileName())
                        .ossUrl(file.getOssUrl())
                        .domainUrl(file.getDomainUrl())
                        .fileSize(file.getFileSize())
                        .build());
            }
            
            resultMap.put("fileInfo", fileInfo);
            resultMap.put("analysisResult", analysisResult);
            
            // Get digital employee
            String digitalEmployee = agentContext.getToolCollection().getDigitalEmployee(getName());
            
            // Send to frontend
            agentContext.getPrinter().send("document_analysis", resultMap, digitalEmployee);
            
        } catch (Exception e) {
            log.error("{} Error notifying frontend", agentContext.getRequestId(), e);
        }
    }
}
