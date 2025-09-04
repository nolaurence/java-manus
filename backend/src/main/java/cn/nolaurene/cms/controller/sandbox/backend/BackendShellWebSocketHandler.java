package cn.nolaurene.cms.controller.sandbox.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class BackendShellWebSocketHandler implements WebSocketHandler {

    @Value("${sandbox.backend.worker-ops-url}")
    private String workerOpsUrl;

    private final ConcurrentHashMap<String, WebSocketSession> workerSessions = new ConcurrentHashMap<>();


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 连接建立时的处理逻辑
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            // 解析: workerId|command
            String[] parts = payload.split("\\|", 2);
            if (parts.length < 2) {
                return;
            }
            String workerId = parts[0];
            String command = parts[1];

            // 获取或创建worker连接
            WebSocketSession workerSession = getOrCreateWorkerSession(session, workerId);
            if (workerSession != null && workerSession.isOpen()) {
                workerSession.sendMessage(new TextMessage(command));
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        // 清理相关的worker连接
        cleanupWorkerSessions(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        // 清理相关的worker连接
        cleanupWorkerSessions(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private WebSocketSession getOrCreateWorkerSession(WebSocketSession clientSession, String workerId) {
        String sessionKey = clientSession.getId() + "_" + workerId;

        return workerSessions.computeIfAbsent(sessionKey, key -> {
            try {
                String workerWsUrl = "ws://" + workerOpsUrl.replace("http://", "").replace("https://", "") + "/worker/shell/wss";
                WebSocketClient client = new StandardWebSocketClient();
                ListenableFuture<WebSocketSession> future = client.doHandshake(
                        new ForwardingWebSocketHandler(clientSession, sessionKey),
                        workerWsUrl
                );
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private void cleanupWorkerSessions(WebSocketSession clientSession) {
        workerSessions.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(clientSession.getId())) {
                try {
                    WebSocketSession workerSession = entry.getValue();
                    if (workerSession != null && workerSession.isOpen()) {
                        workerSession.close();
                    }
                } catch (Exception ignored) {}
                return true;
            }
            return false;
        });
    }

    // 转发处理器
    private class ForwardingWebSocketHandler implements WebSocketHandler {
        private final WebSocketSession clientSession;
        private final String sessionKey;

        public ForwardingWebSocketHandler(WebSocketSession clientSession, String sessionKey) {
            this.clientSession = clientSession;
            this.sessionKey = sessionKey;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            // Worker连接建立成功
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
            if (clientSession.isOpen()) {
                clientSession.sendMessage(message);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            // 移除失效的worker会话
            workerSessions.remove(sessionKey);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            // 移除关闭的worker会话
            workerSessions.remove(sessionKey);
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
    }
}
