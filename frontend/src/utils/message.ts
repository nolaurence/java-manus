import {
  Message,
  type MessageContent,
  type StepContent,
  type ToolContent,
} from '@/types/message';
import { ConversationMessage } from '@/services/api/sandbox';
import type { PlanEventData } from '@/types/sseEvent';

export const attachToolsToSteps = (messages: Message[], conversationMessages: ConversationMessage[]): Message[] => {
  // 收集 steps 和 tools 的索引及时间
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

  let stepPtr = 0;

  for (const tool of tools) {
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
      // 可以挂载
      const stepMsg = messages[candidateStep.index];
      if (stepMsg.type === 'step') {
        if ((stepMsg.content as StepContent).tools === undefined) {
          (stepMsg.content as StepContent).tools = [];
        }
        (stepMsg.content as StepContent).tools.push(messages[tool.index].content as ToolContent);
        toolsToFilter.add(tool.index); // 标记为需删除
      }
    }
    // 否则：tool 在所有 step 之前（包括第一个 step.time > tool.time）
    // → 不挂载，也不加入 toolsToFilter，后续会保留
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
