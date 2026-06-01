import React, { useState, useRef, useEffect, useCallback } from 'react';
import { 
  View, 
  Text,
  StyleSheet, 
  SafeAreaView,
  StatusBar,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '@/navigation/AppNavigator';
import { Message } from '@/types/message';
import { SSEEvent, PlanEventData, ContextEventData } from '@/types/sseEvent';
import { MessageBubble } from '@/components/MessageBubble';
import { ChatInput } from '@/components/ChatInput';
import { LoadingIndicator } from '@/components/LoadingIndicator';
import { PlanPanel } from '@/components/PlanPanel';
import { 
  chatWithAgent, 
  resumeAgentStream, 
  fetchSessionMessages, 
  fetchConversationTitle,
  closeCurrentChat,
} from '@/api/chat';
import { currentUser } from '@/api/auth';
import { 
  mapToFrontendMessage, 
  attachToolsToSteps, 
  mergeReasoningMessages,
  handleSSEEvent,
} from '@/utils/message';
import AsyncStorage from '@react-native-async-storage/async-storage';

type Props = NativeStackScreenProps<RootStackParamList, 'Chat'>;

export const ChatScreen: React.FC<Props> = ({ route, navigation }) => {
  const { agentId, firstMessage } = route.params;
  
  const [messages, setMessages] = useState<Message[]>([]);
  const messagesRef = useRef<Message[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [title, setTitle] = useState('新对话');
  const [plan, setPlan] = useState<PlanEventData | undefined>(undefined);
  const [planMode, setPlanMode] = useState(false);
  const planModeRef = useRef(false);
  const [planPanelVisible, setPlanPanelVisible] = useState(false);
  const [contextUsage, setContextUsage] = useState<ContextEventData | undefined>(undefined);
  
  const flatListRef = useRef<FlatList>(null);
  const sseConnectionRef = useRef<any>(null);
  const isInitializedRef = useRef(false);

  // 保持 ref 同步
  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    planModeRef.current = planMode;
  }, [planMode]);

  // 加载 planMode
  useEffect(() => {
    AsyncStorage.getItem('planMode').then(saved => {
      if (saved !== null) {
        const value = saved === 'true';
        setPlanMode(value);
        planModeRef.current = value;
      }
    });
  }, []);

  // 初始化：加载历史消息并恢复流
  useEffect(() => {
    if (isInitializedRef.current) return;
    isInitializedRef.current = true;

    const init = async () => {
      try {
        const loginInfo = await currentUser();
        const currentUserId = loginInfo?.success && loginInfo.data?.userid
          ? String(loginInfo.data.userid)
          : '';

        if (!currentUserId) {
          Alert.alert('提示', '请先登录');
          navigation.goBack();
          return;
        }

        const titleInfo = await fetchConversationTitle(agentId);
        if (titleInfo?.userId && titleInfo.userId !== currentUserId) {
          Alert.alert('提示', '无权访问此会话');
          navigation.goBack();
          return;
        }

        const history = await fetchSessionMessages(agentId);
        
        if (history.some(item => item.userId && item.userId !== currentUserId)) {
          Alert.alert('提示', '无权访问此会话');
          navigation.goBack();
          return;
        }

        if (history.length === 0) {
          // 新对话，发送首条消息
          const msg = firstMessage || '';
          sendMessage(msg);
          return;
        }

        // 加载历史消息
        const latestContextMessage = [...history].reverse().find(item => item.eventType === 'CONTEXT');
        if (latestContextMessage) {
          setContextUsage(latestContextMessage.content as ContextEventData);
        }

        const renderableHistory = history.filter(item => item.eventType !== 'CONTEXT');
        const mapped = mapToFrontendMessage(renderableHistory);
        const attachedMessages = attachToolsToSteps(mapped, renderableHistory);
        const finalMessages = mergeReasoningMessages(attachedMessages);
        
        setMessages(finalMessages);
        messagesRef.current = finalMessages;
        
        if (titleInfo?.title) {
          setTitle(titleInfo.title);
        }

        // 恢复 SSE 流
        const lastHistoryId = history.reduce((maxId, item) => Math.max(maxId, item.id || 0), 0);
        setIsLoading(true);
        
        sseConnectionRef.current = resumeAgentStream(
          agentId,
          lastHistoryId,
          (event: SSEEvent) => handleEvent(event),
          (error) => {
            console.error('Resume error:', error);
            setIsLoading(false);
          }
        );
      } catch (e) {
        console.error('Init failed:', e);
        // 如果是新对话，尝试发送首条消息
        if (firstMessage) {
          sendMessage(firstMessage);
        }
      }
    };

    init();

    return () => {
      closeCurrentChat();
    };
  }, [agentId]);

  const handleEvent = useCallback((event: SSEEvent) => {
    const currentMessages = messagesRef.current;
    
    handleSSEEvent(event, currentMessages, {
      onUpdateMessages: (newMessages) => {
        setMessages([...newMessages]);
      },
      onSetLoading: setIsLoading,
      onSetTitle: setTitle,
      onSetPlan: (newPlan) => {
        setPlan(newPlan);
        if (newPlan.steps.length > 0) {
          setPlanPanelVisible(false);
        }
      },
      onSetContextUsage: setContextUsage,
    });
  }, []);

  const sendMessage = async (message: string = '') => {
    if (!agentId) return;

    if (message.trim()) {
      const userMsg: Message = {
        type: 'user',
        content: {
          content: message,
          timestamp: Date.now(),
        },
      };
      const newMessages = [...messagesRef.current, userMsg];
      setMessages(newMessages);
      messagesRef.current = newMessages;
    }

    setInputMessage('');
    setIsLoading(true);

    try {
      sseConnectionRef.current = chatWithAgent(
        agentId,
        message,
        planModeRef.current,
        (event: SSEEvent) => handleEvent(event),
        (error) => {
          console.error('Chat error:', error);
          setIsLoading(false);
        }
      );
    } catch (error) {
      console.error('Chat error:', error);
      setIsLoading(false);
    }
  };

  const scrollToBottom = () => {
    if (flatListRef.current && messages.length > 0) {
      flatListRef.current.scrollToEnd({ animated: true });
    }
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const renderItem = ({ item }: { item: Message }) => (
    <MessageBubble 
      message={item} 
      onToolPress={(tool) => {
        // 移动端简化处理，可以后续扩展工具详情页
        Alert.alert('工具调用', `${tool.name}: ${tool.function}`);
      }}
    />
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <KeyboardAvoidingView 
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 0}
      >
        <View style={styles.header}>
          <Text style={styles.headerTitle} numberOfLines={1}>
            {title}
          </Text>
        </View>

        <FlatList
          ref={flatListRef}
          data={messages}
          renderItem={renderItem}
          keyExtractor={(_, index) => `msg-${index}`}
          contentContainerStyle={styles.messageList}
          onContentSizeChange={scrollToBottom}
          onLayout={scrollToBottom}
        />

        {isLoading && (
          <LoadingIndicator />
        )}

        <PlanPanel 
          plan={plan} 
          visible={planPanelVisible}
          onToggle={() => setPlanPanelVisible(!planPanelVisible)}
        />

        <ChatInput
          value={inputMessage}
          onChangeText={setInputMessage}
          onSubmit={() => sendMessage(inputMessage)}
          disabled={isLoading}
          planMode={planMode}
          onPlanModeChange={async (value) => {
            setPlanMode(value);
            planModeRef.current = value;
            await AsyncStorage.setItem('planMode', String(value));
          }}
          placeholder="输入消息..."
        />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e5e5',
    backgroundColor: '#fff',
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    textAlign: 'center',
  },
  messageList: {
    paddingVertical: 8,
  },
});
