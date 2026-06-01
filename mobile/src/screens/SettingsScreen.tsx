import React, { useState, useEffect } from 'react';
import { 
  View, 
  Text, 
  StyleSheet, 
  SafeAreaView,
  StatusBar,
  TextInput,
  TouchableOpacity,
  ScrollView,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { getLlmConfig, updateLlmConfig } from '@/api/settings';
import { useAuth } from '@/context/AuthContext';
import { LlmConfig } from '@/types/api';
import type { TabScreenProps } from '@/navigation/AppNavigator';

type Props = TabScreenProps<'Settings'>;

export const SettingsScreen: React.FC<Props> = () => {
  const { user, logout } = useAuth();
  const [config, setConfig] = useState<LlmConfig>({
    endpoint: '',
    apiKey: '',
    modelName: '',
  });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    try {
      setLoading(true);
      const res = await getLlmConfig();
      if (res.success && res.data) {
        setConfig(res.data);
      }
    } catch (e) {
      console.error('Failed to load config:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      const res = await updateLlmConfig(config);
      if (res.success) {
        Alert.alert('成功', '配置已保存');
      } else {
        Alert.alert('错误', res.message || '保存失败');
      }
    } catch (e: any) {
      Alert.alert('错误', e.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleLogout = () => {
    Alert.alert(
      '退出登录',
      '确定要退出登录吗？',
      [
        { text: '取消', style: 'cancel' },
        { 
          text: '确定', 
          style: 'destructive',
          onPress: () => logout()
        },
      ]
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <KeyboardAvoidingView 
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={styles.header}>
          <Text style={styles.headerTitle}>设置</Text>
        </View>

        <ScrollView style={styles.content} keyboardShouldPersistTaps="handled">
          {/* 用户信息 */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>用户信息</Text>
            <View style={styles.userCard}>
              <Text style={styles.userName}>{user?.name || '未命名'}</Text>
              <Text style={styles.userAccount}>{user?.account || ''}</Text>
            </View>
          </View>

          {/* LLM 配置 */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>LLM 配置</Text>
            
            <Text style={styles.label}>API 端点</Text>
            <TextInput
              style={styles.input}
              value={config.endpoint}
              onChangeText={(text) => setConfig(prev => ({ ...prev, endpoint: text }))}
              placeholder="https://api.example.com/v1"
              autoCapitalize="none"
              autoCorrect={false}
            />

            <Text style={styles.label}>API Key</Text>
            <TextInput
              style={styles.input}
              value={config.apiKey}
              onChangeText={(text) => setConfig(prev => ({ ...prev, apiKey: text }))}
              placeholder="sk-..."
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
            />

            <Text style={styles.label}>模型名称</Text>
            <TextInput
              style={styles.input}
              value={config.modelName}
              onChangeText={(text) => setConfig(prev => ({ ...prev, modelName: text }))}
              placeholder="gpt-4, claude-3 等"
              autoCapitalize="none"
              autoCorrect={false}
            />

            <TouchableOpacity 
              style={[styles.saveButton, saving && styles.saveButtonDisabled]}
              onPress={handleSave}
              disabled={saving}
            >
              <Text style={styles.saveButtonText}>
                {saving ? '保存中...' : '保存配置'}
              </Text>
            </TouchableOpacity>
          </View>

          {/* 退出登录 */}
          <View style={styles.section}>
            <TouchableOpacity 
              style={styles.logoutButton}
              onPress={handleLogout}
            >
              <Text style={styles.logoutButtonText}>退出登录</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
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
  content: {
    flex: 1,
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#333',
    marginBottom: 12,
  },
  userCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
  },
  userName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  userAccount: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
    marginTop: 4,
  },
  input: {
    backgroundColor: '#fff',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 15,
    color: '#333',
    borderWidth: 1,
    borderColor: '#e5e5e5',
    marginBottom: 8,
  },
  saveButton: {
    backgroundColor: '#007AFF',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 10,
  },
  saveButtonDisabled: {
    backgroundColor: '#ccc',
  },
  saveButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  logoutButton: {
    backgroundColor: '#ff3b30',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
  },
  logoutButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
