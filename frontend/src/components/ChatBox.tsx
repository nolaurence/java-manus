import SendIcon from '@/components/icons/SendIcon';
import { createStyles } from 'antd-style';
import React, { useEffect, useState } from 'react';

const useStyles = createStyles((utils) => {
  const css = utils.css;
  return {
    container: css`
      display: flex;
      flex-direction: column;
      width: 100%;
      gap: 0.75rem;
      border-radius: 22px;
      transition: all;
      position: relative;
      background-color: var(--fill-input-chat);
      padding-top: 0.75rem;
      padding-bottom: 0.75rem;
      max-height: 300px;
      box-shadow: 0px 12px 32px 0px rgba(0, 0, 0, 0.02);
      border: 1px solid rgba(0, 0, 0, 0.08);

      &.selected {
        border-color: rgba(0, 0, 0, 0.2);
      }

      &.dark & {
        border-color: var(--border-main);
      }
    `,
    scrollArea: css`
      overflow-y: auto;
      padding-left: 1rem;
      padding-right: 0.5rem;
    `,
    textarea: css`
      width: 100%;
      resize: none;
      min-height: 40px;
      height: 46px;
      font-size: 15px;
      border: none;
      outline: none;
      background: var(--fill-input-chat);
      padding: 0;
      margin: 0;
      overflow: hidden;
      color: inherit;
      font-family: inherit;
      font-weight: normal;
      line-height: 1.5;
      box-shadow: none;
    `,
    footer: css`
      display: flex;
      justify-content: space-between;
      width: 100%;
      padding-left: 0.75rem;
      padding-right: 0.75rem;
    `,
    actionsLeft: css`
      display: flex;
      gap: 0.5rem;
      align-items: center;
    `,
    actionsRight: css`
      display: flex;
      gap: 0.5rem;
    `,
    sendButton: css`
      width: 2rem;
      height: 2rem;
      border-radius: 9999px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background-color 0.2s, opacity 0.2s;
      background-color: var(--Button-primary-black);
      color: white;
      cursor: pointer;
      outline: none;
      border: none;

      &:hover {
        opacity: 0.9;
      }
    `,
    sendButtonDisabled: css`
      width: 2rem;
      height: 2rem;
      border-radius: 9999px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background-color 0.2s, opacity 0.2s;
      color: white;
      outline: none;
      border: none;
      background-color: var(--fill-tsp-white-dark);
      cursor: not-allowed;

      &:hover {
        opacity: 0.9;
      }
    `
  };
});

interface ChatInputProps {
  modelValue: string;
  rows?: number;
  onSubmit?: () => void;
  onUpdateModelValue: (value: string) => void;
  disabled?: boolean;
}

const ChatInput: React.FC<ChatInputProps> = ({
  modelValue,
  rows = 1,
  onSubmit,
  onUpdateModelValue,
  disabled = false
}) => {
  const [isComposing, setIsComposing] = useState(false);
  const [isSendDisabled, setIsSendDisabled] = useState(true);
  const [ chatboxSelected, setChatboxSelected ] = useState(false);
  const [ textAreaHeight, setTextAreaHeight ] = useState("24px");

  const { styles } = useStyles();

  // Update disabled state based on input value
  useEffect(() => {
    setIsSendDisabled(modelValue.trim() === '');
  }, [modelValue]);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    onUpdateModelValue(e.target.value);
  };

  const handleSubmit = () => {
    if ((isSendDisabled || disabled) || !onSubmit) return;
    onSubmit();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (isComposing) return;

    if (e.key === 'Enter' && !e.shiftKey && !(isSendDisabled || disabled)) {
      e.preventDefault();
      handleSubmit();
    }
  };

  useEffect(() => {
    const newHeight = 12 * rows + 4;
    setTextAreaHeight(`${Math.min(300, newHeight)}px`);
  }, [rows]);

  return (
    <div className="flex flex-col bg-[var(--background-gray-main)] sticky bottom-0">
      <div className="[&amp;:not(:empty)]:pb-2 bg-[var(--background-gray-main)] rounded-[22px_22px_0px_0px]"></div>
      <div className="pb-3 relative bg-[var(--background-gray-main)]">
        <div
          className="flex flex-col gap-3 rounded-[22px] transition-all relative bg-[var(--fill-input-chat)] py-3 max-h-[300px] shadow-[0px_12px_32px_0px_rgba(0,0,0,0.02)] border border-black/8 dark:border-[var(--border-main)]"
          style={{
            borderColor: chatboxSelected
              ? 'rgba(0,0,0,0.2)'
              : 'rgba(0, 0, 0, 0.08)',
          }}
          onClick={() => setChatboxSelected(true)}
          onBlur={() => setChatboxSelected(false)}
        >
          <div className="overflow-y-auto pl-4 pr-2">
            <textarea
              rows={rows}
              value={modelValue}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              onCompositionStart={() => setIsComposing(true)}
              onCompositionEnd={() => setIsComposing(false)}
              placeholder="给 Manus 一个任务..."
              className="flex rounded-md border-input focus-visible:outline-none focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 overflow-hidden flex-1 bg-transparent p-0 pt-[1px] border-0 focus-visible:ring-0 focus-visible:ring-offset-0 w-full placeholder:text-[var(--text-disable)] text-[15px] shadow-none resize-none min-h-[28px]"
              style={{ height: textAreaHeight }}
            />
          </div>
          <footer className="px-3 flex gap-2 item-center">
            <div className="flex gap-2 item-center flex-shrink-0"></div>
            <div className="min-w-0 flex gap-2 ml-auto flex-shrink">
              <button
                type="button"
                onClick={handleSubmit}
                disabled={isSendDisabled || disabled}
                className={
                  isSendDisabled || disabled
                    ? styles.sendButtonDisabled
                    : styles.sendButton
                }
              >
                <SendIcon disabled={isSendDisabled || disabled} />
              </button>
            </div>
          </footer>
        </div>
      </div>
    </div>
  );
};

export default ChatInput;
