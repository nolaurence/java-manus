import React, { useState } from 'react';
import { 
  View, 
  TextInput, 
  TouchableOpacity, 
  StyleSheet, 
  ActivityIndicator,
  Keyboard,
  Switch,
  Text,
} from 'react-native';

interface ChatInputProps {
  value: string;
  onChangeText: (text: string) => void;
  onSubmit: () => void;
  disabled?: boolean;
  planMode?: boolean;
  onPlanModeChange?: (value: boolean) => void;
  placeholder?: string;
}

export const ChatInput: React.FC<ChatInputProps> = ({
  value,
  onChangeText,
  onSubmit,
  disabled = false,
  planMode = false,
  onPlanModeChange,
  placeholder = '输入消息...',
}) => {
  const handleSubmit = () => {
    if (value.trim() && !disabled) {
      Keyboard.dismiss();
      onSubmit();
    }
  };

  return (
    <View style={styles.container}>
      {onPlanModeChange && (
        <View style={styles.planModeRow}>
          <Text style={styles.planModeLabel}>计划模式</Text>
          <Switch
            value={planMode}
            onValueChange={onPlanModeChange}
            disabled={disabled}
            trackColor={{ false: '#ccc', true: '#007AFF' }}
          />
        </View>
      )}
      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor="#999"
          multiline
          maxLength={4000}
          editable={!disabled}
          onSubmitEditing={handleSubmit}
          blurOnSubmit={false}
        />
        <TouchableOpacity
          style={[styles.sendButton, (!value.trim() || disabled) && styles.sendButtonDisabled]}
          onPress={handleSubmit}
          disabled={!value.trim() || disabled}
        >
          {disabled ? (
            <ActivityIndicator size="small" color="#fff" />
          ) : (
            <Text style={styles.sendButtonText}>➤</Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderTopColor: '#e5e5e5',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  planModeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    marginBottom: 8,
  },
  planModeLabel: {
    fontSize: 13,
    color: '#666',
    marginRight: 8,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  input: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    paddingRight: 12,
    fontSize: 15,
    maxHeight: 120,
    minHeight: 40,
    color: '#333',
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#007AFF',
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 8,
  },
  sendButtonDisabled: {
    backgroundColor: '#ccc',
  },
  sendButtonText: {
    color: '#fff',
    fontSize: 18,
    marginLeft: 2,
  },
});
