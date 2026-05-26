export interface Skill {
  id: string;
  name: string;
  description?: string;
  version?: string;
  tags?: string[];
  enabled: boolean;
  installedAt?: string;
  updatedAt?: string;
}

const BASE_URL = process.env.BASE_URL || 'http://192.168.49.247:7001';

export async function listSkills(userId: string): Promise<Skill[]> {
  const response = await fetch(`${BASE_URL}/api/skills/${userId}`);
  const data = await response.json();
  if (!data.success) {
    throw new Error(data.message || 'Failed to list skills');
  }
  return data.data || [];
}

export async function installSkill(
  userId: string,
  fileName: string,
  contentBase64: string
): Promise<Skill> {
  const response = await fetch(`${BASE_URL}/api/skills/${userId}/install`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fileName, contentBase64 }),
  });
  const data = await response.json();
  if (!data.success) {
    throw new Error(data.message || 'Failed to install skill');
  }
  return data.data;
}

export async function toggleSkill(
  userId: string,
  skillId: string,
  enabled: boolean
): Promise<Skill> {
  const response = await fetch(`${BASE_URL}/api/skills/${userId}/${skillId}/enabled`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });
  const data = await response.json();
  if (!data.success) {
    throw new Error(data.message || 'Failed to toggle skill');
  }
  return data.data;
}

export async function getAgentSkills(agentId: string): Promise<Skill[]> {
  const response = await fetch(`${BASE_URL}/api/skills/agent/${agentId}`);
  const data = await response.json();
  if (!data.success) {
    throw new Error(data.message || 'Failed to get agent skills');
  }
  return data.data || [];
}

export async function setAgentSkills(agentId: string, skillIds: string[]): Promise<void> {
  const response = await fetch(`${BASE_URL}/api/skills/agent/${agentId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ skillIds }),
  });
  const data = await response.json();
  if (!data.success) {
    throw new Error(data.message || 'Failed to set agent skills');
  }
}
