package com.jd.genie.service;

import com.jd.genie.agent.dto.RawFileRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 原始文件处理服务
 * 专门负责原始文件的处理和准备，不进行任何LLM分析
 * 修改后的实现：消除不必要的LLM调用，避免双重LLM处理
 */
@Slf4j
@Service
public class RawFileService implements IRawFileService {

    /**
     * 处理原始文件：仅进行文件处理和准备，不进行LLM分析
     * 修改后的实现：消除不必要的LLM调用，避免双重LLM处理
     *
     * @param rawFileRequest 包含原始文件内容的请求
     * @return 文件处理确认消息
     */
    @Override
    public String processRawFile(RawFileRequest rawFileRequest) {
        log.info("{} 开始处理原始文件（无LLM分析）: {}", rawFileRequest.getRequestId(), rawFileRequest.getFileName());

        try {
            // 验证文件请求的基本信息
            if (rawFileRequest.getFileName() == null || rawFileRequest.getFileName().trim().isEmpty()) {
                log.error("{} 文件名为空", rawFileRequest.getRequestId());
                return null;
            }

            if (rawFileRequest.getRawBytes() == null || rawFileRequest.getRawBytes().trim().isEmpty()) {
                log.error("{} 文件内容为空", rawFileRequest.getRequestId());
                return null;
            }

            // 记录文件信息（用于调试和审计）
            log.info("{} 原始文件已准备就绪 - 文件名: {}, 描述: {}, 内容大小: {} bytes",
                    rawFileRequest.getRequestId(),
                    rawFileRequest.getFileName(),
                    rawFileRequest.getDescription(),
                    rawFileRequest.getRawBytes().length());

            // 返回简单的确认消息，不进行LLM分析
            String confirmationMessage = String.format(
                "文件 '%s' 已成功上传并准备就绪。文件将在后续的对话中作为上下文使用。",
                rawFileRequest.getFileName()
            );

            log.info("{} ✅ 原始文件处理完成（跳过LLM分析）: {}",
                    rawFileRequest.getRequestId(), rawFileRequest.getFileName());

            return confirmationMessage;

        } catch (Exception e) {
            log.error("{} 处理原始文件时发生错误: {}",
                    rawFileRequest.getRequestId(), rawFileRequest.getFileName(), e);
            return null;
        }
    }
}
