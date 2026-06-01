import EventSource from 'react-native-sse';
import { BASE_URL } from '@/constants/config';
import { SSEEvent } from '@/types/sseEvent';

export type SSEMessageHandler = (event: SSEEvent) => void;
export type SSEErrorHandler = (error: Error) => void;

/**
 * 后端可能发送的所有 SSE 事件类型
 */
const ALL_EVENT_TYPES = [
  'message',
  'tool',
  'step',
  'error',
  'done',
  'title',
  'plan',
  'context',
  'compact',
  'heartbeat',
  'RESUMED',
  'TASK_ALREADY_FINISHED',
  'TASK_ALREADY_FAILED',
  'TASK_FINISHED_BG',
];

/**
 * 使用 react-native-sse 建立 SSE 连接
 * 支持流式接收消息
 */
export function createSSEConnection(
  url: string,
  options: {
    method?: string;
    body?: string;
    headers?: Record<string, string>;
    onMessage: SSEMessageHandler;
    onError?: SSEErrorHandler;
    onOpen?: () => void;
    onClose?: () => void;
  }
): EventSource {
  const es = new EventSource(url, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    body: options.body,
    pollingInterval: 0,
  });

  // 监听所有可能的事件类型
  ALL_EVENT_TYPES.forEach((eventType) => {
    es.addEventListener(eventType, (event: any) => {
      try {
        // react-native-sse 的事件结构: { type, data, id, message }
        const eventName = event.type || eventType;
        const eventData = event.data;
        
        // 处理心跳消息
        if (eventName === 'heartbeat') {
          console.debug('SSE heartbeat received:', eventData);
          return;
        }

        if (eventName && eventName.trim() !== '') {
          const parsedData = eventData ? JSON.parse(eventData) : {};
          options.onMessage({
            event: eventName,
            data: parsedData,
          } as SSEEvent);
        }
      } catch (err) {
        console.error('Failed to parse SSE message:', err, event);
      }
    });
  });

  es.addEventListener('open', () => {
    console.log('SSE connection opened');
    options.onOpen?.();
  });

  es.addEventListener('error', (event: any) => {
    console.error('SSE error:', event);
    if (options.onError) {
      options.onError(new Error(event?.message || 'SSE connection error'));
    }
  });

  es.addEventListener('close', () => {
    console.log('SSE connection closed');
    options.onClose?.();
  });

  return es;
}

/**
 * 关闭 SSE 连接
 */
export function closeSSEConnection(es: EventSource | null) {
  if (es) {
    try {
      es.close();
    } catch (e) {
      console.error('Error closing SSE:', e);
    }
  }
}
