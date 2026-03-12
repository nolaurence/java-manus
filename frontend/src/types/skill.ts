// Skill 类型定义 - 遵循 https://agentskills.io/specification 规范

/**
 * Skill 定义接口
 * 遵循 Agent Skills Specification (https://agentskills.io/specification)
 */
export interface SkillDefinition {
  /** Skill ID (格式: author/name 或 name) */
  skillId: string;

  /**
   * Skill 名称
   * - 1-64 字符
   * - 只能包含小写字母、数字和连字符
   * - 不能以连字符开头或结尾
   * - 不能包含连续连字符
   */
  name: string;

  /**
   * Skill 描述
   * - 1-1024 字符
   * - 描述技能功能和何时使用
   */
  description: string;

  /** 版本号 */
  version: string;

  /** 许可证（可选） */
  license?: string;

  /**
   * 兼容性说明（可选）
   * - 最多 500 字符
   * - 说明环境要求、系统包、网络访问等
   */
  compatibility?: string;

  /**
   * 元数据（可选）
   * - 任意键值对
   */
  metadata?: Record<string, string>;

  /**
   * 允许使用的工具列表（可选，实验性）
   * - 空格分隔的工具列表
   * - 例如: "Bash(git:*) Bash(jq:*) Read"
   */
  allowedTools?: string;

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
  description: string;
  version?: string;
  license?: string;
  compatibility?: string;
  metadata?: Record<string, string>;
  allowedTools?: string;
  documents?: SkillDocumentRequest[];
}

export interface SkillUpdateRequest {
  name?: string;
  description?: string;
  version?: string;
  license?: string;
  compatibility?: string;
  metadata?: Record<string, string>;
  allowedTools?: string;
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
  userId?: number;
}
