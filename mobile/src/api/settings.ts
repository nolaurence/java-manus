import client from './client';
import { ApiResponse, LlmConfig } from '@/types/api';

export async function getLlmConfig(): Promise<ApiResponse<LlmConfig>> {
  return client.get('/api/settings/llm-config') as Promise<ApiResponse<LlmConfig>>;
}

export async function updateLlmConfig(config: LlmConfig): Promise<ApiResponse<void>> {
  return client.post('/api/settings/llm-config', config) as Promise<ApiResponse<void>>;
}
