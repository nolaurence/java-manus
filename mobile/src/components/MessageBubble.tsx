import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import Markdown from 'react-native-markdown-display';
import { Message, MessageContent, ToolContent, StepContent } from '@/types/message';

interface MessageBubbleProps {
  message: Message;
  onToolPress?: (tool: ToolContent) => void;
}

export const MessageBubble: React.FC<MessageBubbleProps> = ({ message, onToolPress }) => {
  const renderUserMessage = (content: MessageContent) => (
    <View style={[styles.bubble, styles.userBubble]}>
      <Text style={styles.userText}>{content.content}</Text>
    </View>
  );

  const renderAssistantMessage = (content: MessageContent) => (
    <View style={[styles.bubble, styles.assistantBubble]}>
      {content.reasoningContent ? (
        <View style={styles.reasoningContainer}>
          <Text style={styles.reasoningLabel}>深度思考</Text>
          <Text style={styles.reasoningText}>{content.reasoningContent}</Text>
        </View>
      ) : null}
      {content.content ? (
        <Markdown style={markdownStyles}>
          {content.content}
        </Markdown>
      ) : null}
    </View>
  );

  const renderToolMessage = (content: ToolContent) => (
    <TouchableOpacity 
      style={[styles.bubble, styles.toolBubble]}
      onPress={() => onToolPress?.(content)}
    >
      <Text style={styles.toolName}>🔧 {content.name}</Text>
      <Text style={styles.toolFunction}>{content.function}</Text>
    </TouchableOpacity>
  );

  const renderStepMessage = (content: StepContent) => {
    const getStatusIcon = () => {
      switch (content.status) {
        case 'completed': return '✅';
        case 'running': return '⏳';
        case 'failed': return '❌';
        default: return '⏸️';
      }
    };

    return (
      <View style={[styles.bubble, styles.stepBubble]}>
        <View style={styles.stepHeader}>
          <Text style={styles.stepIcon}>{getStatusIcon()}</Text>
          <Text style={styles.stepDescription}>{content.description}</Text>
        </View>
        {content.tools && content.tools.length > 0 ? (
          <View style={styles.stepTools}>
            {content.tools.map((tool, idx) => (
              <TouchableOpacity 
                key={idx}
                style={styles.stepToolItem}
                onPress={() => onToolPress?.(tool)}
              >
                <Text style={styles.stepToolText}>🔧 {tool.name}</Text>
              </TouchableOpacity>
            ))}
          </View>
        ) : null}
      </View>
    );
  };

  const getContainerStyle = () => {
    switch (message.type) {
      case 'user': return styles.userContainer;
      default: return styles.assistantContainer;
    }
  };

  return (
    <View style={[styles.container, getContainerStyle()]}>
      {message.type === 'user' && renderUserMessage(message.content as MessageContent)}
      {message.type === 'assistant' && renderAssistantMessage(message.content as MessageContent)}
      {message.type === 'tool' && renderToolMessage(message.content as ToolContent)}
      {message.type === 'step' && renderStepMessage(message.content as StepContent)}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginVertical: 4,
    paddingHorizontal: 12,
  },
  userContainer: {
    alignItems: 'flex-end',
  },
  assistantContainer: {
    alignItems: 'flex-start',
  },
  bubble: {
    maxWidth: '85%',
    padding: 12,
    borderRadius: 16,
  },
  userBubble: {
    backgroundColor: '#007AFF',
    borderBottomRightRadius: 4,
  },
  userText: {
    color: '#fff',
    fontSize: 15,
    lineHeight: 20,
  },
  assistantBubble: {
    backgroundColor: '#f0f0f5',
    borderBottomLeftRadius: 4,
  },
  toolBubble: {
    backgroundColor: '#e8f4fd',
    borderWidth: 1,
    borderColor: '#b8daff',
    borderBottomLeftRadius: 4,
  },
  toolName: {
    fontSize: 13,
    fontWeight: '600',
    color: '#0066cc',
  },
  toolFunction: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  stepBubble: {
    backgroundColor: '#f8f9fa',
    borderWidth: 1,
    borderColor: '#dee2e6',
    borderBottomLeftRadius: 4,
    width: '85%',
  },
  stepHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stepIcon: {
    fontSize: 16,
    marginRight: 8,
  },
  stepDescription: {
    fontSize: 14,
    color: '#333',
    flex: 1,
  },
  stepTools: {
    marginTop: 8,
    paddingLeft: 24,
  },
  stepToolItem: {
    paddingVertical: 4,
  },
  stepToolText: {
    fontSize: 12,
    color: '#0066cc',
  },
  reasoningContainer: {
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    padding: 8,
    marginBottom: 8,
  },
  reasoningLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#666',
    marginBottom: 4,
  },
  reasoningText: {
    fontSize: 13,
    color: '#888',
    lineHeight: 18,
  },
});

const markdownStyles = {
  body: {
    color: '#333',
    fontSize: 15,
    lineHeight: 22,
  },
  code_inline: {
    backgroundColor: '#f4f4f4',
    padding: 2,
    borderRadius: 3,
    fontFamily: 'monospace',
    fontSize: 13,
  },
  code_block: {
    backgroundColor: '#f4f4f4',
    padding: 12,
    borderRadius: 8,
    fontFamily: 'monospace',
    fontSize: 13,
  },
  fence: {
    backgroundColor: '#f4f4f4',
    padding: 12,
    borderRadius: 8,
    fontFamily: 'monospace',
    fontSize: 13,
  },
};
