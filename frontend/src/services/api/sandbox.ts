// @ts-ignore
/* eslint-disable */
import request from '@/services/request';
import {fetchEventSource, type EventSourceMessage} from '@microsoft/fetch-event-source';
import { message } from 'antd';
import type {SSEEvent} from '@/types/sseEvent';

// const BASE_URL = window.location.hostname === 'localhost' ? "https://manus.proxy.nolaurence.cn" : window.location.origin;
const BASE_URL = window.location.hostname === 'localhost' ? "http://192.168.49.250:7001" : window.location.origin;

// streaming related apis
// export async function startStream(streamId: string) {
//   return request<API.Response<boolean>>(`/worker/stream/start/${streamId}`, {
//     method: 'GET',
//   });
// }
//
// export async function stopStream(streamId: string) {
//   return request<API.Response<boolean>>(`/worker/stream/stop/${streamId}`, {
//     method: 'GET',
//   });
// }
//
// export async function startBrowser() {
//   return request<API.Response<boolean>>('/worker/stream/start-browser', {
//     method: 'GET',
//   });
// }
//
// export async function stopBrowser() {
//   return request<API.Response<boolean>>('/worker/stream/close-browser', {
//     method: 'GET',
//   });
// }

export interface Agent {
  agentId: string;
  status: string;
  message: string;
}

// ===================== Conversations =====================
export interface SessionSummary {
  sessionId: string;
  userId: string;
  messageCount: number;
  lastMessageTime?: string;
  lastMessage?: string;
}

export interface ConversationMessage {
  id: number;
  userId: string;
  sessionId: string;
  messageType: 'USER' | 'ASSISTANT';
  content: string;
  metadata?: string;
  createdTime: string;
  updatedTime?: string;
}

export async function fetchUserSessions(userId: string): Promise<SessionSummary[]> {
  const res = await request<API.Response<SessionSummary[]>>(`/conversations/sessions`, {
    method: 'GET',
    params: { userId },
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to fetch sessions');
  }
  // @ts-ignore
  return res.data || [];
}

export async function fetchSessionMessages(sessionId: string): Promise<ConversationMessage[]> {
  const res = await request<API.Response<ConversationMessage[]>>(`/conversations/messages`, {
    method: 'GET',
    params: { sessionId },
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to fetch messages');
  }
  // @ts-ignore
  return res.data || [];
}

/**
 * Create Agent
 */
export async function createAgent(): Promise<Agent> {
  const createAgentFn = async () => {
    return request<API.Response<Agent>>('/agents/', {method: 'POST'});
  };

  const response = await createAgentFn();
  if (!response || !response.success) {
    console.error("Failed to create agent");
    return Promise.reject(response.message);
  }
  // @ts-ignore
  return response.data;
}

export const getVNCUrl = (agentId: string): string => {
  // Convert http to ws, https to wss
  const wsBaseUrl = "wss://localhost:8000";
  return `${wsBaseUrl}/agents/${agentId}/vnc`;
}

/**
 * Chat with Agent ( using SSE protocol )
 */
export const chatWithAgent = async (
  agentId: string,
  message: string = '',
  onMessage: (event: SSEEvent) => void,
  onError?: (error: Error) => void
) => {
  try {
    const apiUrl = `${BASE_URL}/agents/${agentId}/chat`;

    await fetchEventSource(apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      openWhenHidden: true,
      body: JSON.stringify({message, timestamp: Math.floor(Date.now() / 1000)}),
      onmessage(event: EventSourceMessage) {
        if (event.event && event.event.trim() !== '') {
          onMessage({
            event: event.event as SSEEvent['event'],
            data: JSON.parse(event.data) as SSEEvent['data']
          });
        }
      },
      onerror(err) {
        console.error('EventSource error:', err);
        if (onError) {
          onError(err instanceof Error ? err : new Error(String(err)));
        }
        throw err;
      },
    });
  } catch (error) {
    console.error('Chat error:', error);
    if (onError) {
      onError(error instanceof Error ? error : new Error(String(error)));
    }
    throw error;
  }
};

export interface ConsoleRecord {
  ps1: string;
  command: string;
  output: string;
}

export interface ShellViewResponse {
  output: string;
  session_id: string;
  console: ConsoleRecord[];
}

/**
 * View Shell session output
 * @param agentId Agent ID
 * @param sessionId Shell session ID
 * @returns Shell session output content
 */
export async function viewShellSession(agentId: string, sessionId: string): Promise<ShellViewResponse> {

  const viewShellSessionFn = async (sessionId: string) => {
    return request<API.Response<ShellViewResponse>>('/agents', {
      method: 'POST',
      data: {sessionId: sessionId}
    });
  };

  const response = await viewShellSessionFn(sessionId);
  // Error handling
  if (!response || !response.success) {
    console.error("Failed to view shell session");
    return Promise.reject(response.message);
  }
  // @ts-ignore
  return response.data;
}

export interface FileViewResponse {
  content: string;
  file: string;
}

/**
 * View file content
 * @param agentId Agent ID
 * @param file File path
 * @returns File content
 */
export async function viewFile(agentId: string, file: string): Promise<FileViewResponse> {
  const viewFileFn = async (file: string) => {
    return request<API.Response<FileViewResponse>>(`/agents/${agentId}/file`, {
      method: 'POST',
      data: {file},
    });
  };
  const response = await viewFileFn(file);
  // Error handling
  if (!response || !response.success) {
    message.error("Failed to view file");
    return Promise.reject("Failed to view file");
  }
  // @ts-ignore
  return response.data;
}

/**
 * start browser streaming
 */
export async function startStream(agentId: string) {
  const requestFunction = async (agentId: string) => {
    return request<API.Response<boolean>>(`/proxy/stream/start/${agentId}`, {
      method: 'GET',
    });
  };
  const response = await requestFunction(agentId);
  // Error handling
  if (!response || !response.success) {
    message.error("Failed to start stream");
    return Promise.reject("Failed to view file");
  }
  // @ts-ignore
  return response.data;
}

export async function stopStream(agentId: string) {
  const stopRequestFn = async (agentId: string) => {
    return request<API.Response<boolean>>(`/proxy/stream/stop/${agentId}`, {
      method: 'GET',
    });
  };
  const response = await stopRequestFn(agentId);
  // Error handling
  if (!response || !response.success) {
    message.error("failed to stop stream");
    return Promise.reject("failed to stop stream");
  }
  // @ts-ignore
  return response.data;
}
