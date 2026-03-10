// @ts-ignore
/* eslint-disable */
import request from '@/services/request';
import type {
  SkillDefinition,
  SkillRegisterRequest,
  SkillUpdateRequest,
  SkillDocument,
  SkillDocumentRequest,
  SkillExecutionRequest,
  SkillExecutionResult,
  SkillExecutionLog,
  SkillListParams,
} from '@/types/skill';

// ===================== Skills CRUD =====================

/**
 * 注册新 Skill
 */
export async function registerSkill(data: SkillRegisterRequest): Promise<SkillDefinition> {
  const res = await request<API.Response<SkillDefinition>>('/api/skills', {
    method: 'POST',
    data,
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to register skill');
  }
  // @ts-ignore
  return res.data;
}

/**
 * 从 SKILL.md 内容注册 Skill
 */
export async function registerSkillFromMd(content: string): Promise<SkillDefinition> {
  const res = await request<API.Response<SkillDefinition>>('/api/skills/from-md', {
    method: 'POST',
    data: { content },
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to register skill from markdown');
  }
  // @ts-ignore
  return res.data;
}

/**
 * 获取 Skill 列表
 */
export async function listSkills(params?: SkillListParams): Promise<SkillDefinition[]> {
  const res = await request<API.Response<SkillDefinition[]>>('/api/skills', {
    method: 'GET',
    params,
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to list skills');
  }
  // @ts-ignore
  return res.data || [];
}

/**
 * 获取 Skill 详情
 */
export async function getSkill(skillId: string): Promise<SkillDefinition> {
  const res = await request<API.Response<SkillDefinition>>(`/api/skills/${skillId}`, {
    method: 'GET',
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to get skill');
  }
  // @ts-ignore
  return res.data;
}

/**
 * 更新 Skill
 */
export async function updateSkill(skillId: string, data: SkillUpdateRequest): Promise<SkillDefinition> {
  const res = await request<API.Response<SkillDefinition>>(`/api/skills/${skillId}`, {
    method: 'PUT',
    data,
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to update skill');
  }
  // @ts-ignore
  return res.data;
}

/**
 * 删除 Skill
 */
export async function deleteSkill(skillId: string): Promise<void> {
  const res = await request<API.Response<void>>(`/api/skills/${skillId}`, {
    method: 'DELETE',
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to delete skill');
  }
}

/**
 * 启用 Skill
 */
export async function enableSkill(skillId: string): Promise<void> {
  const res = await request<API.Response<void>>(`/api/skills/${skillId}/enable`, {
    method: 'POST',
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to enable skill');
  }
}

/**
 * 禁用 Skill
 */
export async function disableSkill(skillId: string): Promise<void> {
  const res = await request<API.Response<void>>(`/api/skills/${skillId}/disable`, {
    method: 'POST',
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to disable skill');
  }
}

/**
 * 检查 Skill 是否存在
 */
export async function skillExists(skillId: string): Promise<boolean> {
  const res = await request<API.Response<boolean>>(`/api/skills/${skillId}/exists`, {
    method: 'GET',
  });
  if (!res || !res.success) {
    return false;
  }
  // @ts-ignore
  return res.data || false;
}

// ===================== Skill Documents =====================

/**
 * 添加 Skill 文档
 */
export async function addSkillDocument(skillId: string, data: SkillDocumentRequest): Promise<SkillDocument> {
  const res = await request<API.Response<SkillDocument>>(`/api/skills/${skillId}/documents`, {
    method: 'POST',
    data,
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to add document');
  }
  // @ts-ignore
  return res.data;
}

/**
 * 获取 Skill 文档列表
 */
export async function getSkillDocuments(skillId: string, docType?: string): Promise<SkillDocument[]> {
  const url = docType
    ? `/api/skills/${skillId}/documents/${docType}`
    : `/api/skills/${skillId}/documents`;
  const res = await request<API.Response<SkillDocument[]>>(url, {
    method: 'GET',
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to get documents');
  }
  // @ts-ignore
  return res.data || [];
}

// ===================== Skill Execution =====================

/**
 * 执行 Skill
 */
export async function executeSkill(data: SkillExecutionRequest): Promise<SkillExecutionResult> {
  const res = await request<API.Response<SkillExecutionResult>>(`/api/skills/${data.skillId}/execute`, {
    method: 'POST',
    data: { toolName: data.toolName, params: data.params, sessionId: data.sessionId },
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to execute skill');
  }
  // @ts-ignore
  return res.data;
}

/**
 * 匹配 Skill
 */
export async function matchSkills(input: string): Promise<SkillDefinition[]> {
  const res = await request<API.Response<SkillDefinition[]>>('/api/skills/match', {
    method: 'POST',
    data: { input },
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to match skills');
  }
  // @ts-ignore
  return res.data || [];
}

// ===================== Cache Management =====================

/**
 * 刷新缓存
 */
export async function refreshSkillCache(): Promise<void> {
  const res = await request<API.Response<void>>('/api/skills/cache/refresh', {
    method: 'POST',
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to refresh cache');
  }
}

/**
 * 预热缓存
 */
export async function warmupSkillCache(): Promise<void> {
  const res = await request<API.Response<void>>('/api/skills/cache/warmup', {
    method: 'POST',
  });
  if (!res || !res.success) {
    return Promise.reject(res?.message || 'Failed to warmup cache');
  }
}
