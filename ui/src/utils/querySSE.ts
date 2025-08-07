import { fetchEventSource, EventSourceMessage } from '@microsoft/fetch-event-source';

const customHost = SERVICE_BASE_URL || '';
const DEFAULT_SSE_URL = `${customHost}/AutoAgent`;

const SSE_HEADERS = {
  'Content-Type': 'application/json',
  'Cache-Control': 'no-cache',
  'Connection': 'keep-alive',
  'Accept': 'text/event-stream',
};

interface SSEConfig {
  body: any;
  handleMessage: (data: any) => void;
  handleError: (error: Error) => void;
  handleClose: () => void;
  headers?: Record<string, string>;
}

/**
 * 创建服务器发送事件（SSE）连接
 * @param config SSE 配置
 * @param url 可选的自定义 URL
 */
export default (config: SSEConfig, url: string = DEFAULT_SSE_URL): void => {
  const { body = null, handleMessage, handleError, handleClose, headers = {} } = config;

  fetchEventSource(url, {
    method: 'POST',
    credentials: 'include',
    headers: { ...SSE_HEADERS, ...headers },
    body: JSON.stringify(body),
    openWhenHidden: true,
    onmessage(event: EventSourceMessage) {
      if (event.data) {
        // 处理心跳消息（纯文本）
        if (event.data === 'heartbeat') {
          console.log('SSE heartbeat received');
          return;
        }
        
        try {
          const parsedData = JSON.parse(event.data);
          handleMessage(parsedData);
        } catch (error) {
          console.error('Error parsing SSE message:', error, 'Raw data:', event.data);
          handleError(new Error('Failed to parse SSE message'));
        }
      }
    },
    onerror(error: Error) {
      console.error('SSE error:', error);
      handleError(error);
    },
    onclose() {
      console.log('SSE connection closed');
      handleClose();
    }
  });
};
