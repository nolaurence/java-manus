import React, { useState, useEffect, useCallback } from 'react';
import { 
  View, 
  Text, 
  StyleSheet, 
  SafeAreaView,
  StatusBar,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  Alert,
} from 'react-native';
import { fetchUserSessions } from '@/api/chat';
import { currentUser } from '@/api/auth';
import { SessionSummary } from '@/types/api';
import type { TabScreenProps } from '@/navigation/AppNavigator';

type Props = TabScreenProps<'History'>;

export const HistoryScreen: React.FC<Props> = ({ navigation }) => {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [userId, setUserId] = useState('');

  useEffect(() => {
    loadUserInfo();
  }, []);

  const loadUserInfo = async () => {
    try {
      const res = await currentUser();
      if (res.success && res.data?.userid) {
        const uid = String(res.data.userid);
        setUserId(uid);
        loadSessions(uid);
      }
    } catch (e) {
      console.error('Failed to load user info:', e);
    }
  };

  const loadSessions = async (uid: string) => {
    try {
      setRefreshing(true);
      const data = await fetchUserSessions(uid);
      setSessions(data.sort((a, b) => {
        const timeA = a.lastMessageTime ? new Date(a.lastMessageTime).getTime() : 0;
        const timeB = b.lastMessageTime ? new Date(b.lastMessageTime).getTime() : 0;
        return timeB - timeA;
      }));
    } catch (e) {
      console.error('Failed to load sessions:', e);
    } finally {
      setRefreshing(false);
    }
  };

  const onRefresh = useCallback(() => {
    if (userId) {
      loadSessions(userId);
    }
  }, [userId]);

  const formatTime = (timeStr?: string) => {
    if (!timeStr) return '';
    const date = new Date(timeStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
    if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`;
    return date.toLocaleDateString('zh-CN');
  };

  const getStatusIcon = (status?: string) => {
    switch (status) {
      case 'running': return '🔵';
      case 'completed': return '✅';
      case 'failed': return '❌';
      default: return '⚪';
    }
  };

  const renderItem = ({ item }: { item: SessionSummary }) => (
    <TouchableOpacity
      style={styles.sessionItem}
      onPress={() => {
        navigation.navigate('Chat', { 
          agentId: item.sessionId,
          firstMessage: '',
        });
      }}
    >
      <View style={styles.sessionHeader}>
        <Text style={styles.sessionIcon}>{item.icon || '💬'}</Text>
        <Text style={styles.sessionTitle} numberOfLines={1}>
          {item.title || '未命名会话'}
        </Text>
        <Text style={styles.statusIcon}>{getStatusIcon(item.status)}</Text>
      </View>
      <View style={styles.sessionFooter}>
        <Text style={styles.lastMessage} numberOfLines={1}>
          {item.lastMessage || '暂无消息'}
        </Text>
        <Text style={styles.timeText}>{formatTime(item.lastMessageTime)}</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.header}>
        <Text style={styles.headerTitle}>历史会话</Text>
      </View>

      <FlatList
        data={sessions}
        renderItem={renderItem}
        keyExtractor={(item) => item.sessionId}
        contentContainerStyle={styles.list}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>暂无历史会话</Text>
          </View>
        }
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e5e5e5',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  list: {
    padding: 12,
  },
  sessionItem: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  sessionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  sessionIcon: {
    fontSize: 18,
    marginRight: 8,
  },
  sessionTitle: {
    flex: 1,
    fontSize: 15,
    fontWeight: '600',
    color: '#333',
  },
  statusIcon: {
    fontSize: 12,
  },
  sessionFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  lastMessage: {
    flex: 1,
    fontSize: 13,
    color: '#666',
    marginRight: 8,
  },
  timeText: {
    fontSize: 12,
    color: '#999',
  },
  emptyContainer: {
    paddingTop: 100,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 15,
    color: '#999',
  },
});
