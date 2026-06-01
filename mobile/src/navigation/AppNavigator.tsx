import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator, type BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Text, View, ActivityIndicator } from 'react-native';
import { useAuth } from '@/context/AuthContext';
import { HomeScreen } from '@/screens/HomeScreen';
import { ChatScreen } from '@/screens/ChatScreen';
import { HistoryScreen } from '@/screens/HistoryScreen';
import { SettingsScreen } from '@/screens/SettingsScreen';
import { LoginScreen } from '@/screens/LoginScreen';

export type RootStackParamList = {
  Login: undefined;
  Main: undefined;
  Chat: { agentId: string; firstMessage: string };
};

export type HomeTabParamList = {
  Home: undefined;
  History: undefined;
  Settings: undefined;
};

// 辅助类型：Tab Screen 可以访问 Stack Navigator
export type TabScreenProps<T extends keyof HomeTabParamList> = CompositeScreenProps<
  BottomTabScreenProps<HomeTabParamList, T>,
  NativeStackScreenProps<RootStackParamList>
>;

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<HomeTabParamList>();

const TabIcon: React.FC<{ icon: string; focused: boolean }> = ({ icon, focused }) => (
  <Text style={{ fontSize: 22, opacity: focused ? 1 : 0.5 }}>
    {icon}
  </Text>
);

const MainTabs = () => (
  <Tab.Navigator
    screenOptions={{
      headerShown: false,
      tabBarStyle: {
        borderTopWidth: 1,
        borderTopColor: '#e5e5e5',
      },
    }}
  >
    <Tab.Screen 
      name="Home" 
      component={HomeScreen}
      options={{
        tabBarLabel: '首页',
        tabBarIcon: ({ focused }) => <TabIcon icon="🏠" focused={focused} />,
      }}
    />
    <Tab.Screen 
      name="History" 
      component={HistoryScreen}
      options={{
        tabBarLabel: '历史',
        tabBarIcon: ({ focused }) => <TabIcon icon="💬" focused={focused} />,
      }}
    />
    <Tab.Screen 
      name="Settings" 
      component={SettingsScreen}
      options={{
        tabBarLabel: '设置',
        tabBarIcon: ({ focused }) => <TabIcon icon="⚙️" focused={focused} />,
      }}
    />
  </Tab.Navigator>
);

export const AppNavigator: React.FC = () => {
  const { isLoading, isLoggedIn } = useAuth();

  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator 
        screenOptions={{ headerShown: false }}
        initialRouteName={isLoggedIn ? 'Main' : 'Login'}
      >
        <Stack.Screen name="Login" component={LoginScreen} />
        <Stack.Screen name="Main" component={MainTabs} />
        <Stack.Screen 
          name="Chat" 
          component={ChatScreen}
          options={{ gestureEnabled: false }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
};
