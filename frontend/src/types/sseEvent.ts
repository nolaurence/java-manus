export type SSEEvent = {
  event: 'tool' | 'step' | 'message' | 'error' | 'done' | 'title' | 'plan' | 'context';
  data: ToolEventData | StepEventData | MessageEventData | ErrorEventData | DoneEventData | TitleEventData | PlanEventData | ContextEventData;
}

export interface ToolEventData {
  timestamp: number;
  name: string;
  function: string;
  args: {[key: string]: any};
}

export interface StepEventData {
  timestamp: number;
  status: "pending" | "running" | "completed" | "failed"
  id: string
  description: string
  toolIds?: number[];
}

export interface MessageEventData {
  timestamp: number;
  content: string;
  contentDelta: string;
  reasoningContent: string;
  reasoningContentDelta: string;
}

export interface ErrorEventData {
  timestamp: number;
  error: string;
}

export interface DoneEventData {
  timestamp: number;
}

export interface TitleEventData {
  timestamp: number;
  title: string;
}

export interface PlanEventData {
  timestamp: number;
  steps: StepEventData[];
}

export interface ContextEventData {
  timestamp: number;
  usedTokens: number;
  maxTokens: number;
  percent: number;
  compacted?: boolean;
}
