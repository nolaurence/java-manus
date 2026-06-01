export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
}

export interface UserInfo {
  account?: string;
  name?: string;
  avatar?: string;
  userid?: number;
  email?: string;
  signature?: string;
  title?: string;
  group?: string;
  notifyCount?: number;
  unreadCount?: number;
  country?: string;
  access?: string;
  address?: string;
  phone?: string;
  status?: number;
  role: number;
}

export interface LoginParams {
  account?: string;
  password?: string;
}

export interface RegisterParams {
  account?: string;
  password?: string;
  checkPassword?: string;
  name?: string;
  inviteCode?: string;
  gender?: number;
  email?: string;
  phone?: string;
}

export interface LoginResult {
  success?: boolean;
  data?: UserInfo;
  code?: string;
  message?: string;
}

export interface AgentInfo {
  agentId: string;
  status: string;
  message: string;
}

export interface SessionSummary {
  sessionId: string;
  userId: string;
  messageCount: number;
  lastMessageTime?: string;
  lastMessage?: string;
  title?: string;
  icon?: string;
  status?: string;
}

export interface ConversationMessage {
  id: number;
  userId: string;
  sessionId: string;
  messageType: 'USER' | 'ASSISTANT';
  eventType: 'MESSAGE' | 'TOOL' | 'STEP' | 'PLAN' | 'ERROR' | 'DONE' | 'TITLE' | 'CONTEXT';
  content: object;
  metadata?: string;
  createdTime: string;
  updatedTime?: string;
}

export interface LlmConfig {
  endpoint: string;
  apiKey: string;
  modelName: string;
}

export interface ChatRequest {
  message: string;
  planMode: boolean;
  timestamp: number;
}

export interface ShellViewResponse {
  output: string;
  session_id: string;
  console: ConsoleRecord[];
}

export interface ConsoleRecord {
  ps1: string;
  command: string;
  output: string;
}

export interface FileViewResponse {
  content: string;
  file: string;
}
