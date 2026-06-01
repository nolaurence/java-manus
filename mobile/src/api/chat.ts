import client from './client';
import { createSSEConnection, closeSSEConnection, SSEMessageHandler, SSEErrorHandler } from './sse';
import { BASE_URL } from '@/constants/config';
import { 
  ApiResponse, 
  AgentInfo, 
  SessionSummary, 
  ConversationMessage, 
  ChatRequest,
  ShellViewResponse,
  FileViewResponse
} from '@/types/api';

// ===================== Agent =====================

export async function createAgent(): Promise<AgentInfo> {
  const res = await client.post('/agents/') as ApiResponse<AgentInfo>;
  if (!res || !res.success) {
    throw new Error(res?.message || 'Failed to create agent');
  }
  return res.data!;
}

// ===================== Conversations =====================

export async function fetchUserSessions(userId: string): Promise<SessionSummary[]> {
  const res = await client.get('/conversations/sessions', { params: { userId } }) as ApiResponse<SessionSummary[]>;
  if (!res || !res.success) {
    throw new Error(res?.message || 'Failed to fetch sessions');
  }
  return res.data || [];
}

export async function fetchSessionMessages(sessionId: string): Promise<ConversationMessage[]> {
  const res = await client.get('/conversations/messages', { params: { sessionId } }) as ApiResponse<ConversationMessage[]>;
  if (!res || !res.success) {
    throw new Error(res?.message || 'Failed to fetch messages');
  }
  return res.data || [];
}

export async function fetchConversationTitle(sessionId: string): Promise<{sessionId: string; userId?: string; title: string; icon?: string} | null> {
  try {
    const res = await client.get('/conversations/title', { params: { sessionId } }) as ApiResponse<{sessionId: string; userId?: string; title: string; icon?: string}>;
    if (!res || !res.success) {
      return null;
    }
    return res.data || null;
  } catch {
    return null;
  }
}

// ===================== Streaming Chat =====================

let currentSSEConnection: any = null;

/**
 * 与 Agent 进行流式对话
 * @param agentId Agent ID
 * @param message 用户消息
 * @param planMode 是否启用计划模式
 * @param onMessage 消息回调
 * @param onError 错误回调
 * @returns EventSource 实例（可用于手动关闭）
 */
export function chatWithAgent(
  agentId: string,
  message: string,
  planMode: boolean,
  onMessage: SSEMessageHandler,
  onError?: SSEErrorHandler
) {
  // 关闭已有连接
  if (currentSSEConnection) {
    closeSSEConnection(currentSSEConnection);
  }

  const apiUrl = `${BASE_URL}/agents/${agentId}/chat`;
  const body: ChatRequest = {
    message,
    planMode,
    timestamp: Math.floor(Date.now() / 1000),
  };

  const es = createSSEConnection(apiUrl, {
    method: 'POST',
    body: JSON.stringify(body),
    onMessage,
    onError: (err) => {
      console.error('Chat SSE error:', err);
      onError?.(err);
    },
  });

  currentSSEConnection = es;
  return es;
}

/**
 * 恢复 Agent 的 SSE 流（断线重连）
 * @param agentId Agent ID
 * @param afterId 最后一条消息的ID
 * @param onMessage 消息回调
 * @param onError 错误回调
 * @returns EventSource 实例
 */
export function resumeAgentStream(
  agentId: string,
  afterId: number,
  onMessage: SSEMessageHandler,
  onError?: SSEErrorHandler
) {
  if (currentSSEConnection) {
    closeSSEConnection(currentSSEConnection);
  }

  const apiUrl = `${BASE_URL}/agents/${agentId}/resume?afterId=${afterId || 0}`;

  const es = createSSEConnection(apiUrl, {
    method: 'POST',
    onMessage: (event) => {
      // 过滤特殊事件
      const specialEvents = ['heartbeat', 'RESUMED', 'TASK_ALREADY_FINISHED', 'TASK_ALREADY_FAILED', 'TASK_FINISHED_BG'];
      if (specialEvents.includes(event.event)) {
        if (event.event !== 'heartbeat' && event.event !== 'RESUMED') {
          onMessage({ event: 'done', data: { timestamp: Date.now() } });
        }
        return;
      }
      onMessage(event);
    },
    onError: (err) => {
      console.error('Resume SSE error:', err);
      onError?.(err);
    },
    onClose: () => {
      onMessage({ event: 'done', data: { timestamp: Date.now() } });
    },
  });

  currentSSEConnection = es;
  return es;
}

/**
 * 关闭当前 SSE 连接
 */
export function closeCurrentChat() {
  if (currentSSEConnection) {
    closeSSEConnection(currentSSEConnection);
    currentSSEConnection = null;
  }
}

// ===================== Tool Views =====================

export async function viewShellSession(agentId: string, sessionId: string): Promise<ShellViewResponse> {
  const res = await client.post(`/agents/${agentId}/shell`, { sessionId }) as ApiResponse<ShellViewResponse>;
  if (!res || !res.success) {
    throw new Error(res?.message || 'Failed to view shell session');
  }
  return res.data!;
}

export async function viewFile(agentId: string, file: string): Promise<FileViewResponse> {
  const res = await client.post(`/agents/${agentId}/file`, { file }) as ApiResponse<FileViewResponse>;
  if (!res || !res.success) {
    throw new Error(res?.message || 'Failed to view file');
  }
  return res.data!;
}
