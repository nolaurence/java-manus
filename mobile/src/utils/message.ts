import { Message, MessageContent, ToolContent, StepContent } from '@/types/message';
import { ConversationMessage } from '@/types/api';
import { SSEEvent, MessageEventData, ToolEventData, StepEventData, PlanEventData } from '@/types/sseEvent';

/**
 * 将后端历史消息转换为前端消息格式
 */
export function mapToFrontendMessage(history: ConversationMessage[]): Message[] {
  const messages: Message[] = [];
  
  for (const item of history) {
    const content = typeof item.content === 'string' 
      ? JSON.parse(item.content) 
      : item.content;
    
    switch (item.eventType) {
      case 'MESSAGE': {
        const msgData = content as MessageEventData;
        if (item.messageType === 'USER') {
          messages.push({
            type: 'user',
            content: {
              content: msgData.content || '',
              timestamp: msgData.timestamp || Date.now(),
            },
          });
        } else {
          messages.push({
            type: 'assistant',
            content: {
              content: msgData.content || '',
              reasoningContent: msgData.reasoningContent || undefined,
              timestamp: msgData.timestamp || Date.now(),
            },
          });
        }
        break;
      }
      case 'TOOL': {
        const toolData = content as ToolEventData;
        messages.push({
          type: 'tool',
          content: {
            name: toolData.name,
            function: toolData.function,
            args: toolData.args,
            timestamp: toolData.timestamp || Date.now(),
          },
        });
        break;
      }
      case 'STEP': {
        const stepData = content as StepEventData;
        messages.push({
          type: 'step',
          content: {
            id: stepData.id,
            description: stepData.description,
            status: stepData.status,
            tools: [],
            toolIds: stepData.toolIds,
            timestamp: stepData.timestamp || Date.now(),
          },
        });
        break;
      }
      case 'PLAN': {
        const planData = content as PlanEventData;
        messages.push({
          type: 'plan',
          content: {
            timestamp: planData.timestamp || Date.now(),
          },
        });
        break;
      }
      default:
        break;
    }
  }
  
  return messages;
}

/**
 * 将工具消息附加到步骤中
 */
export function attachToolsToSteps(messages: Message[], history: ConversationMessage[]): Message[] {
  const result: Message[] = [];
  
  for (let i = 0; i < messages.length; i++) {
    const msg = messages[i];
    
    if (msg.type === 'tool') {
      // 尝试找到最近的 step 并附加
      let attached = false;
      for (let j = result.length - 1; j >= 0; j--) {
        const prevMsg = result[j];
        if (prevMsg.type === 'step') {
          const stepContent = prevMsg.content as StepContent;
          if (stepContent.status === 'running') {
            stepContent.tools = stepContent.tools || [];
            stepContent.tools.push(msg.content as ToolContent);
            attached = true;
            break;
          }
        }
        if (prevMsg.type === 'user' || prevMsg.type === 'assistant') {
          break;
        }
      }
      
      if (!attached) {
        result.push(msg);
      }
    } else {
      result.push(msg);
    }
  }
  
  return result;
}

/**
 * 合并 reasoning 消息到主消息中
 */
export function mergeReasoningMessages(messages: Message[]): Message[] {
  const result: Message[] = [];
  
  for (const msg of messages) {
    if (msg.type === 'assistant') {
      const lastMsg = result[result.length - 1];
      if (lastMsg && lastMsg.type === 'assistant') {
        // 合并到上一条 assistant 消息
        const lastContent = lastMsg.content as MessageContent;
        const currentContent = msg.content as MessageContent;
        lastContent.content += currentContent.content;
        if (currentContent.reasoningContent) {
          lastContent.reasoningContent = (lastContent.reasoningContent || '') + currentContent.reasoningContent;
        }
      } else {
        result.push(msg);
      }
    } else {
      result.push(msg);
    }
  }
  
  return result;
}

/**
 * 处理 SSE 事件并更新消息列表
 */
export function handleSSEEvent(
  event: SSEEvent,
  messages: Message[],
  options: {
    onUpdateMessages: (messages: Message[]) => void;
    onSetLoading: (loading: boolean) => void;
    onSetTitle?: (title: string) => void;
    onSetPlan?: (plan: PlanEventData) => void;
    onSetContextUsage?: (context: any) => void;
  }
): Message[] {
  let newMessages = [...messages];
  
  switch (event.event) {
    case 'message': {
      const data = event.data as MessageEventData;
      
      if (data.reasoningContentDelta === '[START]') {
        // 新消息开始
        newMessages.push({
          type: 'assistant',
          content: {
            content: '',
            timestamp: Date.now(),
          },
        });
      } else if (data.reasoningContent) {
        // 完整的 reasoning content
        const lastMsg = newMessages[newMessages.length - 1];
        if (lastMsg && lastMsg.type === 'assistant') {
          const content = { ...lastMsg.content } as MessageContent;
          content.reasoningContent = data.reasoningContent;
          newMessages[newMessages.length - 1] = { ...lastMsg, content };
        }
      } else {
        // 增量内容
        const lastMsg = newMessages[newMessages.length - 1];
        if (lastMsg && lastMsg.type === 'assistant') {
          const content = { ...lastMsg.content } as MessageContent;
          if (data.reasoningContentDelta) {
            content.reasoningContent = (content.reasoningContent || '') + data.reasoningContentDelta;
          }
          if (data.contentDelta) {
            content.content += data.contentDelta;
          }
          newMessages[newMessages.length - 1] = { ...lastMsg, content };
        } else {
          newMessages.push({
            type: 'assistant',
            content: {
              content: data.contentDelta || '',
              reasoningContent: data.reasoningContentDelta || undefined,
              timestamp: Date.now(),
            },
          });
        }
      }
      
      options.onUpdateMessages(newMessages);
      break;
    }
    
    case 'tool': {
      const data = event.data as ToolEventData;
      
      // 尝试附加到步骤
      let attached = false;
      for (let i = newMessages.length - 1; i >= 0; i--) {
        const msg = newMessages[i];
        if (msg.type === 'step') {
          const stepContent = { ...msg.content } as StepContent;
          if (stepContent.status === 'running') {
            stepContent.tools = [...(stepContent.tools || [])];
            const toolContent: ToolContent = {
              name: data.name,
              function: data.function,
              args: data.args,
              timestamp: data.timestamp,
            };
            // 检查是否已存在
            const exists = stepContent.tools.some(
              t => t.timestamp === data.timestamp && t.name === data.name && t.function === data.function
            );
            if (!exists) {
              stepContent.tools.push(toolContent);
              newMessages[i] = { ...msg, content: stepContent };
            }
            attached = true;
            break;
          }
        }
        if (msg.type === 'user') break;
      }
      
      if (!attached) {
        newMessages.push({
          type: 'tool',
          content: {
            name: data.name,
            function: data.function,
            args: data.args,
            timestamp: data.timestamp,
          },
        });
      }
      
      options.onUpdateMessages(newMessages);
      break;
    }
    
    case 'step': {
      const data = event.data as StepEventData;
      
      if (data.status === 'running') {
        newMessages.push({
          type: 'step',
          content: {
            id: data.id,
            description: data.description,
            status: data.status,
            tools: [],
            toolIds: data.toolIds,
            timestamp: data.timestamp,
          },
        });
      } else if (data.status === 'completed') {
        // 更新最后一个 running 的 step
        for (let i = newMessages.length - 1; i >= 0; i--) {
          const message = newMessages[i];
          if (message.type === 'step') {
            const stepContent: StepContent = { ...message.content };
            stepContent.status = 'completed';
            newMessages[i] = { ...message, content: stepContent };
            break;
          }
        }
      } else if (data.status === 'failed') {
        options.onSetLoading(false);
      }
      
      options.onUpdateMessages(newMessages);
      break;
    }
    
    case 'error': {
      const data = event.data as any;
      options.onSetLoading(false);
      newMessages.push({
        type: 'assistant',
        content: {
          content: data.error || '发生错误',
          timestamp: data.timestamp || Date.now(),
        },
      });
      options.onUpdateMessages(newMessages);
      break;
    }
    
    case 'done': {
      options.onSetLoading(false);
      break;
    }
    
    case 'title': {
      const data = event.data as any;
      options.onSetTitle?.(data.title);
      break;
    }
    
    case 'plan': {
      const data = event.data as PlanEventData;
      options.onSetPlan?.(data);
      break;
    }
    
    case 'context': {
      options.onSetContextUsage?.(event.data);
      break;
    }
  }
  
  return newMessages;
}
