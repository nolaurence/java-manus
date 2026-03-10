// Skill 类型定义

export interface TriggerConfig {
  type: 'keyword' | 'regex' | 'intent';
  pattern: string;
  confidence?: number;
}

export interface ToolParameter {
  type: string;
  description: string;
  required?: boolean;
  default?: string | number | boolean;
}

export interface ToolDefinition {
  name: string;
  description: string;
  parameters?: Record<string, ToolParameter>;
  executor: 'shell' | 'http';
  command?: string;
  httpEndpoint?: string;
  httpMethod?: string;
  timeout?: number;
}

export interface RequiresConfig {
  bins?: string[];
  env?: Record<string, string>;
  config?: Record<string, string>;
}

export interface SkillDefinition {
  skillId: string;
  name: string;
  version: string;
  author: string;
  description: string;
  category?: string;
  triggers?: TriggerConfig[];
  tools?: ToolDefinition[];
  requires?: RequiresConfig;
  osSupport?: string[];
  priority?: number;
  userId?: number;
  status?: number;
  createdTime?: string;
  updatedTime?: string;
}

export interface SkillDocument {
  id: number;
  skillId: string;
  docType: 'SKILL_MD' | 'README' | 'REFERENCE' | 'EXAMPLE' | 'SCRIPT' | 'OTHER';
  content: string;
  description?: string;
  createdTime?: string;
  updatedTime?: string;
}

// Request types
export interface SkillRegisterRequest {
  name: string;
  version?: string;
  author: string;
  description: string;
  category?: string;
  triggers?: TriggerConfig[];
  tools?: ToolDefinition[];
  requires?: RequiresConfig;
  osSupport?: string[];
  priority?: number;
  documents?: SkillDocumentRequest[];
}

export interface SkillUpdateRequest {
  name?: string;
  version?: string;
  description?: string;
  category?: string;
  triggers?: TriggerConfig[];
  tools?: ToolDefinition[];
  requires?: RequiresConfig;
  osSupport?: string[];
  priority?: number;
}

export interface SkillDocumentRequest {
  docType: string;
  content: string;
  description?: string;
}

export interface SkillExecutionRequest {
  skillId: string;
  toolName?: string;
  params?: Record<string, any>;
  sessionId?: string;
}

// Response types
export interface SkillExecutionResult {
  skillId: string;
  toolName: string;
  status: 'success' | 'failed' | 'timeout';
  output?: string;
  error?: string;
  durationMs?: number;
  metadata?: Record<string, any>;
}

export interface SkillExecutionLog {
  id: number;
  skillId: string;
  toolName: string;
  inputParams?: string;
  output?: string;
  status: string;
  errorMessage?: string;
  durationMs?: number;
  sessionId?: string;
  userId?: number;
  createdTime?: string;
}

// List query params
export interface SkillListParams {
  category?: string;
  userId?: number;
}
