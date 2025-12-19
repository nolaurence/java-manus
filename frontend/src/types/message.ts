export type MessageType = "user" | "assistant" | "tool" | "step" | "plan";

export type Message = 
  | { type: 'user'; content: MessageContent }
  | { type: 'assistant'; content: MessageContent }
  | { type: 'tool'; content: ToolContent }
  | { type: 'step'; content: StepContent }
  | { type: 'plan'; content: BaseContent };

export interface BaseContent {
  timestamp: number;
}

export interface MessageContent extends BaseContent {
  content: string;
}

export interface ToolContent extends BaseContent {
  name: string;
  function: string;
  args: any;
  result?: any;
}

export interface StepContent extends BaseContent {
  id: string;
  description: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  tools: ToolContent[];
}
