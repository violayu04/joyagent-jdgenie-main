package com.jd.genie.agent.tool.common;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 文档分析工具 - 已禁用版本
 * 
 * 为了确保"上传文档功能"只将原始内容发送给LLM，不进行任何后端分析，
 * 此工具已被禁用。所有文档分析现在完全由LLM负责。
 */
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
        return "文档分析工具已禁用。所有文档分析现在直接由LLM处理，无需后端预处理。请使用file_tool直接上传文档。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> command = new HashMap<>();
        command.put("type", "string");
        command.put("description", "此工具已禁用，请使用file_tool进行文档上传");
        command.put("enum", Arrays.asList("disabled"));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("command", command);
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("command"));

        return parameters;
    }

    @Override
    public Object execute(Object input) {
        log.info("{} DocumentAnalyzerTool已禁用 - 文档分析现在完全由LLM处理", agentContext.getRequestId());
        
        return "📝 文档分析工具已禁用。\n\n" +
               "🎯 新的工作方式：\n" +
               "• 所有文档现在直接发送给LLM进行分析\n" +
               "• 后端不再进行任何文本提取或预处理\n" +
               "• LLM完全负责文档的解读和分析\n\n" +
               "✅ 请直接使用file_tool上传文档，系统会自动将原始内容发送给LLM。";
    }
}
