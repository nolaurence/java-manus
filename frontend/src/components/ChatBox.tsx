import React, { useEffect, useState } from 'react';
import { Sender } from '@ant-design/x';
import { LinkOutlined } from '@ant-design/icons';
import { Button, Popover, Flex } from 'antd';


interface ChatInputProps {
  modelValue: string;
  onSubmit?: () => void;
  onUpdateModelValue: (value: string) => void;
  disabled?: boolean;
}

const ChatInput: React.FC<ChatInputProps> = ({
  modelValue,
  onSubmit,
  onUpdateModelValue,
  disabled = false
}) => {
  const [isComposing, setIsComposing] = useState(false);
  const [isSendDisabled, setIsSendDisabled] = useState(true);

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

  return (
    <Sender
      placeholder="给 Manus 一个任务..."
      value={modelValue}
      onChange={(value => onUpdateModelValue(value))}
      styles={{
        content: { borderTopLeftRadius: 12, borderTopRightRadius: 12, overflow: 'hidden', backgroundColor: 'white' },
        footer: { borderBottomLeftRadius: 12, borderBottomRightRadius: 12, overflow: 'hidden', backgroundColor: 'white' },
      }}
      onSubmit={handleSubmit}
      actions={false}
      footer={({ components }) => {
        const { SendButton, LoadingButton, SpeechButton } = components;

        return (
          <Flex justify="space-between" align="center" >
            <Flex gap="small" align="cemter" >
              <Button style={{ fontSize: 18 }} type="text" icon={<LinkOutlined />} />
            </Flex>
            <Flex align="center">
              {disabled ? (
                <LoadingButton type="default" style={{ color: 'black' }} />
              ) : (
                <SendButton type="primary" style={{ backgroundColor: 'black' }} disabled={false} />
              )}
            </Flex>
          </Flex>
        )
      }}
    />
  );
};

export default ChatInput;
