import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { PlanEventData } from '@/types/sseEvent';

interface PlanPanelProps {
  plan: PlanEventData | undefined;
  visible: boolean;
  onToggle: () => void;
}

export const PlanPanel: React.FC<PlanPanelProps> = ({ plan, visible, onToggle }) => {
  if (!plan || plan.steps.length === 0) return null;

  const completedSteps = plan.steps.filter(s => s.status === 'completed').length;
  const runningStep = plan.steps.find(s => s.status === 'running');

  if (!visible) {
    return (
      <TouchableOpacity style={styles.collapsed} onPress={onToggle}>
        <Text style={styles.collapsedIcon}>
          {completedSteps === plan.steps.length ? '✅' : '⏳'}
        </Text>
        <Text style={styles.collapsedText} numberOfLines={1}>
          {runningStep?.description || '任务完成'}
        </Text>
        <Text style={styles.collapsedProgress}>
          {completedSteps} / {plan.steps.length}
        </Text>
      </TouchableOpacity>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>任务进度</Text>
        <TouchableOpacity onPress={onToggle}>
          <Text style={styles.closeButton}>▼</Text>
        </TouchableOpacity>
      </View>
      <View style={styles.steps}>
        {plan.steps.map((step) => (
          <View key={step.id} style={styles.stepRow}>
            <Text style={styles.stepIcon}>
              {step.status === 'completed' ? '✅' : step.status === 'running' ? '⏳' : '⏸️'}
            </Text>
            <Text style={styles.stepText} numberOfLines={1}>
              {step.description}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#f8f9fa',
    borderTopWidth: 1,
    borderTopColor: '#e5e5e5',
    padding: 12,
    maxHeight: 200,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  title: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  closeButton: {
    fontSize: 14,
    color: '#666',
  },
  steps: {
    gap: 6,
  },
  stepRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stepIcon: {
    fontSize: 14,
    marginRight: 8,
    width: 20,
  },
  stepText: {
    fontSize: 13,
    color: '#555',
    flex: 1,
  },
  collapsed: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#f8f9fa',
    borderTopWidth: 1,
    borderTopColor: '#e5e5e5',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  collapsedIcon: {
    fontSize: 14,
    marginRight: 8,
  },
  collapsedText: {
    flex: 1,
    fontSize: 13,
    color: '#555',
  },
  collapsedProgress: {
    fontSize: 12,
    color: '#999',
    marginLeft: 8,
  },
});
