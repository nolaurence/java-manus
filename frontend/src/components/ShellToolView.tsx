import React, {
  useState,
  useEffect,
  useMemo,
  useCallback,
  forwardRef,
  useImperativeHandle,
  useRef,
} from 'react';
import type { ToolContent } from '@/types/message';
import { message } from 'antd';
import { viewShellSession } from '@/services/api/sandbox'; // 假设这是获取 Shell 会话内容的 API

// 定义组件 Props
interface Props {
  agentId: string;
  toolContent: ToolContent;
}

// 定义可暴露的子组件方法
type ShellSessionRef = {
  loadContent: () => void;
};

// React 组件定义
const ShellSessionComponent = forwardRef<ShellSessionRef, Props>(
  ({ agentId, toolContent }, ref) => {
    const [shell, setShell] = useState('');
    const refreshInterval = useRef<number | null>(null);

    const sessionId = useMemo(() => {
      return toolContent?.args?.id || '';
    }, [toolContent]);

    const loadShellContent = useCallback(() => {
      if (!sessionId) return;

      viewShellSession(agentId, sessionId)
        .then((response) => {
          let newShell = '';
          for (const e of response.console) {
            newShell += `<span style="color: rgb(0, 187, 0);">${e.ps1}</span><span> ${e.command}</span>\n`;
            newShell += `<span>${e.output}</span>\n`;
          }
          setShell(newShell);
        })
        .catch((error) => {
          console.error('Failed to load Shell session content:', error);
          message.error('加载Shell会话内容失败');
        });
    }, [agentId, sessionId]);

    // 暴露方法给父组件
    useImperativeHandle(ref, () => ({
      loadContent: () => {
        loadShellContent();
      },
    }));

    // 组件挂载时启动定时刷新
    useEffect(() => {
      loadShellContent();
      refreshInterval.current = window.setInterval(loadShellContent, 5000);

      return () => {
        if (refreshInterval.current !== null) {
          clearInterval(refreshInterval.current);
          refreshInterval.current = null;
        }
      };
    }, [loadShellContent]);

    // 当 sessionId 变化时重新加载内容
    useEffect(() => {
      if (sessionId) {
        loadShellContent();
      }
    }, [sessionId, loadShellContent]);

    return (
      <>
        <div
          className="h-[36px] flex items-center px-3 w-full bg-[var(--background-gray-main)] border-b border-[var(--border-main)] rounded-t-[12px] shadow-[inset_0px_1px_0px_0px_#FFFFFF] dark:shadow-[inset_0px_1px_0px_0px_#FFFFFF30]"
        >
          <div className="flex-1 flex items-center justify-center">
            <div className="max-w-[250px] truncate text-[var(--text-tertiary)] text-sm font-medium text-center">
              {sessionId}
            </div>
          </div>
        </div>

        <div className="flex-1 min-h-0 w-full overflow-y-auto">
          <div
            dir="ltr"
            data-orientation="horizontal"
            className="flex flex-col flex-1 min-h-0"
          >
            <div
              data-state="active"
              data-orientation="horizontal"
              role="tabpanel"
              id="radix-:r5m:-content-setup"
              tabIndex={0}
              className="py-2 focus-visible:outline-none data-[state=inactive]:hidden flex-1 font-mono text-sm leading-relaxed px-3 outline-none overflow-auto whitespace-pre-wrap break-all"
              style={{ animationDuration: '0s' }}
            >
              <code dangerouslySetInnerHTML={{ __html: shell }} />
            </div>
          </div>
        </div>
      </>
  );
  }
);

export default ShellSessionComponent;
