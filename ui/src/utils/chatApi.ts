import httpClient from './http';
import { getUniqId } from './index';

interface SaveUserMessageRequest {
  sessionId?: string;
  requestId: string;
  query: string;
  deepThink?: number;
  outputStyle?: string;
}

interface SaveUserMessageResponse {
  success: boolean;
  sessionId: string;
  message: string;
  error?: string;
}

/**
 * 立即保存用户消息到会话历史
 * @param message 用户消息内容
 * @param sessionId 可选的会话ID，如果为空将创建新会话
 * @param deepThink 是否深度思考
 * @param outputStyle 输出样式
 * @returns Promise<SaveUserMessageResponse>
 */
export const saveUserMessage = async (
  message: string,
  sessionId?: string,
  deepThink?: boolean,
  outputStyle?: string
): Promise<SaveUserMessageResponse> => {
  const requestData: SaveUserMessageRequest = {
    requestId: getUniqId(),
    query: message,
    deepThink: deepThink ? 1 : 0,
    outputStyle
  };

  if (sessionId) {
    requestData.sessionId = sessionId;
  }

  try {
    const response = await httpClient.post<SaveUserMessageResponse>(
      '/web/api/v1/chat/saveUserMessage',
      requestData
    );
    return response;
  } catch (error) {
    console.error('Failed to save user message:', error);
    throw error;
  }
};