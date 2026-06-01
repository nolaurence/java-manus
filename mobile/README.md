# Java Manus Mobile

基于 React Native + Expo 的 Java Manus 移动端版本，支持 AI Agent 对话的流式输出。

## 技术栈

- **React Native** 0.73
- **Expo** SDK 50
- **TypeScript**
- **React Navigation** v6
- **SSE 流式通信** (react-native-sse)

## 功能特性

- ✅ 用户登录/注册
- ✅ 创建 Agent 会话
- ✅ **流式消息输出**（SSE 实时推送）
- ✅ 对话历史加载与断线恢复
- ✅ 计划模式（Plan Mode）
- ✅ 步骤/工具执行状态展示
- ✅ 历史会话列表
- ✅ LLM 配置管理
- ✅ Markdown 消息渲染
- ✅ 深度思考（Reasoning）内容展示

## 项目结构

```
mobile/
├── App.tsx                          # 应用入口
├── src/
│   ├── api/
│   │   ├── client.ts                # Axios HTTP 客户端
│   │   ├── sse.ts                   # SSE 流式连接封装
│   │   ├── auth.ts                  # 登录相关 API
│   │   ├── chat.ts                  # 聊天/会话 API
│   │   └── settings.ts              # 设置 API
│   ├── components/
│   │   ├── MessageBubble.tsx        # 消息气泡组件
│   │   ├── ChatInput.tsx            # 聊天输入框
│   │   ├── LoadingIndicator.tsx     # 加载动画
│   │   └── PlanPanel.tsx            # 计划面板
│   ├── context/
│   │   └── AuthContext.tsx          # 认证状态管理
│   ├── navigation/
│   │   └── AppNavigator.tsx         # 路由导航配置
│   ├── screens/
│   │   ├── HomeScreen.tsx           # 首页
│   │   ├── ChatScreen.tsx           # 聊天页面
│   │   ├── HistoryScreen.tsx        # 历史会话
│   │   ├── SettingsScreen.tsx       # 设置页面
│   │   └── LoginScreen.tsx          # 登录/注册
│   ├── types/
│   │   ├── api.ts                   # API 类型定义
│   │   ├── message.ts               # 消息类型定义
│   │   └── sseEvent.ts              # SSE 事件类型
│   ├── utils/
│   │   └── message.ts               # 消息转换工具
│   └── constants/
│       └── config.ts                # 全局配置
```

## 快速开始

### 1. 安装依赖

```bash
cd mobile
npm install
# 或
yarn install
```

### 2. 配置后端地址

编辑 `src/constants/config.ts`：

```typescript
export const BASE_URL = __DEV__ 
  ? 'http://你的后端IP:7001'   // 开发环境
  : 'https://你的生产域名.com'; // 生产环境
```

> **注意**：在 iOS 模拟器上可以使用 `http://localhost:7001`，但在 Android 模拟器上需要使用 `http://10.0.2.2:7001`。真机调试需要使用局域网 IP。

### 3. 启动开发服务器

```bash
# iOS
npx expo start --ios

# Android
npx expo start --android

# Web
npx expo start --web
```

## 流式输出实现

移动端通过 `react-native-sse` 库与后端建立 SSE (Server-Sent Events) 连接，实现消息的实时流式推送。

### 核心流程

1. **发送消息**：`chatWithAgent()` 创建 SSE 连接，发送用户消息
2. **接收流**：后端通过 SSE 推送增量消息内容（`contentDelta` / `reasoningContentDelta`）
3. **实时渲染**：前端逐字追加到消息气泡中，实现打字机效果
4. **断线恢复**：`resumeAgentStream()` 支持从历史断点恢复连接，重放丢失的事件

### SSE 事件类型

| 事件 | 说明 |
|------|------|
| `message` | AI 回复内容增量 |
| `tool` | 工具调用事件 |
| `step` | 执行步骤状态 |
| `plan` | 计划面板数据 |
| `title` | 会话标题 |
| `context` | 上下文用量 |
| `error` | 错误信息 |
| `done` | 完成信号 |

## 与前端 Web 的对比

| 特性 | Web 前端 (UmiJS) | 移动端 (React Native) |
|------|------------------|----------------------|
| 框架 | UmiJS 4 + React 18 | Expo + React Native |
| UI 库 | Ant Design X | React Native 原生组件 |
| 流式通信 | `@microsoft/fetch-event-source` | `react-native-sse` |
| 样式 | Tailwind + antd-style | StyleSheet |
| Markdown | react-markdown | react-native-markdown-display |
| 状态管理 | Hooks | Hooks + Context |
| 存储 | localStorage | AsyncStorage |

## 注意事项

1. **网络权限**：确保 `app.json` 中配置了正确的网络权限（Android 需要 `android.permissions.INTERNET`）
2. **HTTP 明文传输**：开发环境使用 HTTP 时，Android 需要配置 `networkSecurityConfig`，iOS 需要配置 `NSAppTransportSecurity`
3. **SSE 超时**：后端 SSE 超时为 30 分钟，移动端连接断开后会自动清理

## 后续可扩展

- [ ] 工具详情页（Shell/文件/浏览器内容查看）
- [ ] 深色模式支持
- [ ] 语音输入
- [ ] 消息复制/分享
- [ ] 推送通知
- [ ] 离线消息缓存
