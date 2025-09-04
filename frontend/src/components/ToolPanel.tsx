import React, { useState, useRef, forwardRef, useImperativeHandle, useEffect } from 'react';
import { Minimize2, Play } from 'lucide-react';
import type { ToolContent } from '@/types/message';
import { useToolInfo } from '@/composables/useTool';

interface ToolPanelProps {
  agentId?: string;
  realTime: boolean;
  onJumpToRealTime: () => void;
  isShow: boolean;
  setIsShow: React.Dispatch<React.SetStateAction<boolean>>;
  content?: ToolContent;
}

const ToolPanel: React.FC<ToolPanelProps> = (
  { agentId, realTime, onJumpToRealTime, isShow, setIsShow, content }
) => {
  const toolViewRef = useRef<any>(null);
  const { toolInfo } = useToolInfo(content);
  // if (isShow) {
  //   debugger;
  // }

  // 当面板显示且内容变化时加载内容
  useEffect(() => {
    if (isShow && toolViewRef.current?.loadContent) {
      toolViewRef.current.loadContent();
    }
  }, [isShow, content]);

  const handleClose = () => {
    setIsShow(false);
  };

  const handleJump = () => {
    onJumpToRealTime();
  };

  return (
    <div
      className={`${
        isShow
          ? 'h-full w-full top-0 ltr:right-0 rtl:left-0 z-50 fixed sm:sticky sm:top-0 sm:right-0 sm:h-[100vh] sm:ml-3 sm:py-3 sm:mr-4'
          : 'h-full overflow-hidden'
      }`}
      style={{
        width: isShow ? '768px' : '0px',
        opacity: isShow ? '1' : '0',
        transition: '0.2s ease-in-out'
      }}
    >
      <div className="h-full" style={{ width: isShow ? '100%' : '0px' }}>
        {isShow && (
          <div className="bg-[var(--background-gray-main)] sm:bg-[var(--background-menu-white)] sm:rounded-[22px] shadow-[0px_0px_8px_0px_rgba(0,0,0,0.02)] border border-black/8 dark:border-[var(--border-light)] flex h-full w-full">
            <div className="flex-1 min-w-0 p-4 flex flex-col h-full">
              <div className="flex items-center gap-2 w-full">
                <div className="text-[var(--text-primary)] text-lg font-semibold flex-1">
                  Manus Computer
                </div>
                {/* eslint-disable-next-line react/button-has-type */}
                <button
                  className="w-7 h-7 relative rounded-md inline-flex items-center justify-center gap-2.5 cursor-pointer hover:bg-[var(--fill-tsp-gray-main)]"
                  onClick={handleClose}
                >
                  <Minimize2 className="w-5 h-5 text-[var(--icon-tertiary)]" />
                </button>
              </div>

              {toolInfo && (
                <div className="flex items-center gap-2 mt-2">
                  <div className="w-[40px] h-[40px] bg-[var(--fill-tsp-gray-main)] rounded-lg flex items-center justify-center flex-shrink-0">
                    {toolInfo.icon && <toolInfo.icon size={28} /> }
                  </div>
                  <div className="flex-1 flex flex-col gap-1 min-w-0">
                    <div className="text-[12px] text-[var(--text-tertiary)]">
                      Manus is using{' '}
                      <span className="text-[var(--text-secondary)]">
                        {toolInfo.name}
                      </span>
                    </div>
                    <div
                      title={`${toolInfo.function} ${toolInfo.functionArg}`}
                      className="max-w-[100%] w-[max-content] truncate text-[13px] rounded-full inline-flex items-center px-[10px] py-[3px] border border-[var(--border-light)] bg-[var(--fill-tsp-gray-main)] text-[var(--text-secondary)]"
                    >
                      {toolInfo.function}
                      <span className="flex-1 min-w-0 px-1 ml-1 text-[12px] font-mono max-w-full text-ellipsis overflow-hidden whitespace-nowrap text-[var(--text-tertiary)]">
                        <code>{toolInfo.functionArg}</code>
                      </span>
                    </div>
                  </div>
                </div>
              )}

              <div className="flex flex-col rounded-[12px] overflow-hidden bg-[var(--background-gray-main)] border border-[var(--border-dark)] dark:border-black/30 shadow-[0px_4px_32px_0px_rgba(0,0,0,0.04)] flex-1 min-h-0 mt-[16px]">
                {toolInfo && toolInfo.view && (
                  <toolInfo.view
                    ref={toolViewRef}
                    agentId={agentId}
                    toolContent={content}
                  />
                )}
                {!realTime && (
                  <div className="mt-auto flex w-full items-center gap-2 px-4 h-[44px] relative">
                    {/* eslint-disable-next-line react/button-has-type */}
                    <button
                      className="h-10 px-3 border border-[var(--border-main)] flex items-center gap-1 bg-[var(--background-white-main)] hover:bg-[var(--background-gray-main)] shadow-[0px_5px_16px_0px_var(--shadow-S),0px_0px_1.25px_0px_var(--shadow-S)] rounded-full cursor-pointer absolute left-[50%] translate-x-[-50%]"
                      style={{ bottom: 'calc(100% + 10px)' }}
                      onClick={handleJump}
                    >
                      <Play size={16} />
                      <span className="text-[var(--text-primary)] text-sm font-medium">
                        Jump to live
                      </span>
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ToolPanel;
