import React, { useState, useMemo } from 'react';
import ManusTextIcon from './icons/ManusTextIcon';
import ToolUse from './ToolUse';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { CheckIcon } from 'lucide-react';
import { Bot } from 'lucide-react';
import type { Message, MessageContent, ToolContent, StepContent } from '@/types/message';
import { useRelativeTime } from '@/composables/useTime';



// 自定义钩子：相对时间计算
// const useRelativeTime = (timestamp: number): string => {
//   // 这里实现相对时间的逻辑
//   const now = new Date();
//   const date = new Date(timestamp);
//   const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);
//
//   if (diffInSeconds < 60) return 'Just now';
//   if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
//   if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
//   return `${Math.floor(diffInSeconds / 86400)}d ago`;
// };

// 主组件
const ChatMessage: React.FC<{
  message: Message;
  onToolClick: (tool: ToolContent) => void;
}> = ({ message, onToolClick }) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const relativeTime = useRelativeTime(message.content.timestamp);

  // 类型断言辅助函数
  const asUserContent = (): MessageContent => message.content as MessageContent;
  const asStepContent = (): StepContent => message.content as StepContent;
  const asToolContent = (): ToolContent => message.content as ToolContent;

  // Markdown渲染函数
  const renderMarkdown = (text: string): string => {
    if (typeof text !== 'string') return '';
    const html = marked(text) as string;
    return DOMPurify.sanitize(html);
  };

  if (!message.content) {
    console.log(`POINT: null content of type: ${message.type}`);
    return (<p>1</p>);
  }

  // 用户消息渲染
  if (message.type === 'user') {
    return (
      <div className="flex w-full flex-col items-end justify-end gap-1 group mt-3">
        <div className="flex items-end">
          <div className="flex items-center justify-end gap-[2px] invisible group-hover:visible">
            <div className="float-right transition text-[12px] text-[var(--text-tertiary)] invisible group-hover:visible">
              {relativeTime}
            </div>
          </div>
        </div>
        <div className="flex max-w-[90%] relative flex-col gap-2 items-end">
          <div
            className="relative flex items-center rounded-[12px] overflow-hidden bg-[var(--fill-white)] dark:bg-[var(--fill-tsp-white-main)] p-3 ltr:rounded-br-none rtl:rounded-bl-none border border-[var(--border-main)] dark:border-0"
            dangerouslySetInnerHTML={{ __html: renderMarkdown(asUserContent().content) }}
          />
        </div>
      </div>
    );
  }

  // 助手消息渲染
  if (message.type === 'assistant') {
    return (
      <div className="flex flex-col gap-2 w-full group mt-3">
        <div className="flex items-center justify-between h-7 group">
          <div className="flex items-center gap-[3px]">
            <Bot size={24} className="w-6 h-6" />
            <ManusTextIcon />
          </div>
          <div className="flex items-center gap-[2px] invisible group-hover:visible">
            <div className="float-right transition text-[12px] text-[var(--text-tertiary)] invisible group-hover:visible">
              {relativeTime}
            </div>
          </div>
        </div>
        <div
          className="max-w-none p-0 m-0 prose prose-sm sm:prose-base dark:prose-invert [&_pre:not(.shiki)]:!bg-[var(--fill-tsp-white-light)] [&_pre:not(.shiki)]:text-[var(--text-primary)] text-base text-[var(--text-primary)]"
          dangerouslySetInnerHTML={{ __html: renderMarkdown(asUserContent().content) }}
        />
      </div>
    );
  }

  // 工具调用渲染
  if (message.type === 'tool') {
    // @ts-ignore
    return <ToolUse tool={asToolContent()} onClick={() => onToolClick(asToolContent())} />;
  }

  // 步骤消息渲染
  if (message.type === 'step') {
    const content = asStepContent();

    return (
      <div className="flex flex-col">
        <div
          className="text-sm w-full clickable flex gap-2 justify-between group/header truncate text-[var(--text-primary)]"
          data-event-id="HNtP7XOMUOhPemItd2EkK2"
        >
          <div className="flex flex-row gap-2 justify-center items-center truncate">
            {content.status !== 'completed' ? (
              <div className="w-4 h-4 flex-shrink-0 flex items-center justify-center border border-[var(--border-dark)] rounded-[15px]"></div>
            ) : (
              <div className="w-4 h-4 flex-shrink-0 flex items-center justify-center border-[var(--border-dark)] rounded-[15px] bg-[var(--text-disable)] dark:bg-[var(--fill-tsp-white-dark)] border-0">
                <CheckIcon
                  className="text-[var(--icon-white)] dark:text-[var(--icon-white-tsp)]"
                  size={10}
                />
              </div>
            )}

            <div
              className="truncate font-medium markdown-content"
              dangerouslySetInnerHTML={{
                __html: content.description ? renderMarkdown(content.description) : ''
              }}
            />

            <span
              className="flex-shrink-0 flex"
              onClick={() => setIsExpanded(!isExpanded)}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="24" height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="lucide lucide-chevron-down transition-transform duration-300 w-4 h-4"
                style={{ transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
              >
                <path d="m6 9 6 6 6-6"></path>
              </svg>
            </span>
          </div>

          <div className="float-right transition text-[12px] text-[var(--text-tertiary)] invisible group-hover/header:visible">
            {relativeTime}
          </div>
        </div>

        <div className="flex">
          <div className="w-[24px] relative">
            <div
              className="border-l border-dashed border-[var(--border-dark)] absolute start-[8px] top-0 bottom-0"
              style={{ height: 'calc(100% + 14px)' }}
            ></div>
          </div>

          <div
            className={`flex flex-col gap-3 flex-1 min-w-0 overflow-hidden pt-2 transition-[max-height,opacity] duration-150 ease-in-out ${
              isExpanded ? 'max-h-[100000px] opacity-100' : 'max-h-0 opacity-0'
            }`}
          >
            {content?.tools?.map((tool, index) => (
              <ToolUse
                key={index}
                // @ts-ignore
                tool={tool as ToolContent}
                onClick={() => onToolClick(tool as ToolContent)}
              />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return null;
};

export default ChatMessage;
