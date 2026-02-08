import {
  Message,
  type MessageContent,
  type StepContent,
  type ToolContent,
} from '@/types/message';
import { ConversationMessage } from '@/services/api/sandbox';
import type { PlanEventData } from '@/types/sseEvent';

export const attachToolsToSteps = (messages: Message[], conversationMessages: ConversationMessage[]): Message[] => {
  // 构建 Tool 消息的数据库 ID -> index 映射
  const toolIdToIndex = new Map<number, number>();
  for (let i = 0; i < messages.length; i++) {
    if (messages[i].type === 'tool') {
      const msgId = conversationMessages[i]?.id;
      if (msgId) {
        toolIdToIndex.set(msgId, i);
      }
    }
  }

  // 收集 steps 和 tools 的索引及时间（用于回退逻辑）
  const steps: { index: number; time: number }[] = [];
  const tools: { index: number; time: number }[] = [];

  for (let i = 0; i < messages.length; i++) {
    const raw = conversationMessages[i];
    const time = new Date(raw.createdTime).getTime();

    if (messages[i].type === 'step') {
      steps.push({ index: i, time });
    } else if (messages[i].type === 'tool') {
      tools.push({ index: i, time });
    }
  }

  // 记录哪些 tool 被成功挂载（需要被过滤）
  const toolsToFilter = new Set<number>();

  // 如果没有任何 step，所有 tool 都保留
  if (steps.length === 0) {
    return messages; // 不做任何过滤
  }

  // 遍历 Step 消息，优先使用 toolIds 进行关联
  for (let i = 0; i < messages.length; i++) {
    const msg = messages[i];
    if (msg.type === 'step') {
      const stepContent = msg.content as StepContent;

      if (!stepContent.tools) {
        stepContent.tools = [];
      }

      // 优先使用 toolIds 精确关联
      if (stepContent.toolIds && stepContent.toolIds.length > 0) {
        for (const toolId of stepContent.toolIds) {
          const toolIndex = toolIdToIndex.get(toolId);
          if (toolIndex !== undefined) {
            const toolMsg = messages[toolIndex];
            if (toolMsg.type === 'tool') {
              stepContent.tools.push(toolMsg.content as ToolContent);
              toolsToFilter.add(toolIndex);
            }
          }
        }
      }
    }
  }

  // 回退逻辑：处理没有 toolIds 的历史数据（使用时间戳关联）
  let stepPtr = 0;
  for (const tool of tools) {
    // 如果这个 tool 已经通过 toolIds 被关联了，跳过
    if (toolsToFilter.has(tool.index)) {
      continue;
    }

    // 移动指针到最后一个 step.time <= tool.time
    while (
      stepPtr < steps.length - 1 &&
      steps[stepPtr + 1].time <= tool.time
    ) {
      stepPtr++;
    }

    const candidateStep = steps[stepPtr];

    // 检查这个 step 是否真的在 tool 之前（或同时）
    if (candidateStep.time <= tool.time) {
      const stepMsg = messages[candidateStep.index];
      if (stepMsg.type === 'step') {
        const stepContent = stepMsg.content as StepContent;
        // 只有当该 step 没有 toolIds 时才使用时间戳关联
        if (!stepContent.toolIds || stepContent.toolIds.length === 0) {
          if (stepContent.tools === undefined) {
            stepContent.tools = [];
          }
          stepContent.tools.push(messages[tool.index].content as ToolContent);
          toolsToFilter.add(tool.index);
        }
      }
    }
  }

  // 过滤：只移除被标记的 tool
  return messages.filter((_, idx) => !toolsToFilter.has(idx));
}

export const mapToFrontendMessage = (conversationMessages: ConversationMessage[]): Message[] => {
  return conversationMessages?.map((m) => {
    if (m.eventType === 'MESSAGE') {
      return {
        type: m.messageType === 'USER' ? 'user' : 'assistant',
        content: m.content as MessageContent,
      };
    } else if (m.eventType === 'PLAN') {
      // @ts-ignore
      return {
        type: 'plan',
        content: m.content as PlanEventData,
      };
    } else if (m.eventType === 'TOOL') {
      return {
        type: 'tool',
        content: m.content as ToolContent,
      };
    } else if (m.eventType === 'STEP') {
      return {
        type: 'step',
        content: m.content as StepContent,
      };
    }
    return {
      type: 'assistant',
      content: m.content as MessageContent,
    };
  });
}
