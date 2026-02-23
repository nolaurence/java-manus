import request from '../request';

export interface LlmConfig {
  endpoint: string;
  apiKey: string;
  modelName: string;
}

/**
 * 获取大模型配置
 */
export async function getLlmConfig() {
  return request<API.Response<LlmConfig>>('/api/settings/llm-config', {
    method: 'GET',
  });
}

/**
 * 更新大模型配置
 */
export async function updateLlmConfig(config: LlmConfig): Promise<void> {
  return request('/api/settings/llm-config', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: config,
  });
}
