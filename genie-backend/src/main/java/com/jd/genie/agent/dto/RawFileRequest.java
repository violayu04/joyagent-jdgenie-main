package com.jd.genie.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于上传原始文件内容直接发送给LLM的请求DTO
 * 后端作为纯粹的管道，不进行任何文本提取或分析
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawFileRequest {
    private String requestId;
    private String fileName;
    private String description;
    /**
     * 原始文件字节数据的base64编码
     * 这是文件的原始二进制内容，编码为base64字符串
     */
    private String rawBytes;
}
