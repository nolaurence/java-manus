import React, { useState, useEffect } from 'react';
import { 
  View, 
  Text, 
  StyleSheet, 
  SafeAreaView,
  StatusBar,
  Alert,
} from 'react-native';
import { ChatInput } from '@/components/ChatInput';
import { createAgent } from '@/api/chat';
import { currentUser } from '@/api/auth';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { TabScreenProps } from '@/navigation/AppNavigator';

type Props = TabScreenProps<'Home'>;

export const HomeScreen: React.FC<Props> = ({ navigation }) => {
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [planMode, setPlanMode] = useState(false);
  const [userName, setUserName] = useState('');

  useEffect(() => {
    loadUserInfo();
    loadPlanMode();
  }, []);

  const loadUserInfo = async () => {
    try {
      const res = await currentUser();
      if (res.success && res.data) {
        setUserName(res.data.name || '');
      }
    } catch (e) {
      console.error('Failed to load user info:', e);
    }
  };

  const loadPlanMode = async () => {
    try {
      const saved = await AsyncStorage.getItem('planMode');
      if (saved !== null) {
        setPlanMode(saved === 'true');
      }
    } catch (e) {
      console.error('Failed to load plan mode:', e);
    }
  };

  const handleSubmit = async () => {
    if (!message.trim() || isSubmitting) return;

    setIsSubmitting(true);
    try {
      const agent = await createAgent();
      await AsyncStorage.setItem('firstMessage', message);
      await AsyncStorage.setItem('agentId', agent.agentId);
      await AsyncStorage.setItem('planMode', String(planMode));
      
      navigation.navigate('Chat', { 
        agentId: agent.agentId,
        firstMessage: message,
      });
    } catch (error) {
      console.error('Failed to create agent:', error);
      Alert.alert('错误', '创建会话失败，请稍后重试');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.header}>
        <Text style={styles.logo}>🤖</Text>
        <Text style={styles.title}>Java Manus</Text>
      </View>

      <View style={styles.content}>
        <View style={styles.greeting}>
          <Text style={styles.greetingText}>
            你好{userName ? `, ${userName}` : ''}
          </Text>
          <Text style={styles.subtitle}>我能为你做什么？</Text>
        </View>

        <View style={styles.inputContainer}>
          <ChatInput
            value={message}
            onChangeText={setMessage}
            onSubmit={handleSubmit}
            disabled={isSubmitting}
            planMode={planMode}
            onPlanModeChange={async (value) => {
              setPlanMode(value);
              await AsyncStorage.setItem('planMode', String(value));
            }}
            placeholder="输入你的问题..."
          />
        </View>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  logo: {
    fontSize: 28,
    marginRight: 10,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#333',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  greeting: {
    marginBottom: 40,
    paddingHorizontal: 4,
  },
  greetingText: {
    fontSize: 32,
    fontWeight: '300',
    color: '#333',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 32,
    fontWeight: '300',
    color: '#999',
  },
  inputContainer: {
    marginTop: 20,
  },
});
