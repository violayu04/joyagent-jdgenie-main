package com.jd.genie.service;

import com.jd.genie.agent.dto.RawFileRequest;

/**
 * 原始文件处理服务接口
 * 专门负责"上传文档功能"：将原始文档内容直接发送给LLM
 */
public interface IRawFileService {
    
    /**
     * 处理原始文件：直接发送给LLM，不进行任何文本提取或分析
     * 
     * @param rawFileRequest 包含原始文件内容的请求
     * @return LLM的处理结果
     */
    String processRawFile(RawFileRequest rawFileRequest);
}
