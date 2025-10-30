package cn.nolaurene.cms.service.sandbox.backend.vnc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * VNC WebSocket 代理处理器
 * 将前端的 WebSocket 连接转发到 worker 的 VNC websockify 服务
 */
@Slf4j
@Component
public class VncWebSocketHandler extends BinaryWebSocketHandler {

    @Value("${sandbox.backend.worker-vnc-url:ws://worker:5902}")
    private String workerVncUrl;

    private final WebSocketClient webSocketClient = new StandardWebSocketClient();
    
    // 使用线程池替代创建新线程
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // 存储前端连接与 worker 连接的映射
    private final Map<String, WebSocketSession> frontendSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> workerSessions = new ConcurrentHashMap<>();
    // 存储连接配对关系
    private final Map<String, String> browserToSandboxSessionIds = new ConcurrentHashMap<>();
    private final Map<String, String> sandboxToBrowserSessionIds = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
        // 1. 提取 session_id 和 signature
        URI uri = browserSession.getUri();
        String path = uri.getPath(); // e.g. /abc123/vnc
        String sessionId = extractSessionId(path);
        String query = uri.getQuery();
        String signature = null;
        
        if (query != null) {
            signature = parseQueryParam(query, "signature");
        }

        log.info("VNC connection established for session: {}, signature: {}", sessionId, signature);
        
        // 存储前端会话
        frontendSessions.put(sessionId, browserSession);

        // 4. 连接到沙箱 WebSocket（二进制模式）
        connectToSandbox(browserSession, workerVncUrl, sessionId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession browserSession, BinaryMessage message) throws Exception {
        String sessionId = getSessionId(browserSession);
        if (sessionId != null) {
            WebSocketSession workerSession = workerSessions.get(sessionId);
            if (workerSession != null && workerSession.isOpen()) {
                workerSession.sendMessage(message);
            } else {
                log.warn("Worker session not found or not open for session: {}", sessionId);
            }
        } else {
            log.warn("Could not find session ID for browser session");
        }
        super.handleBinaryMessage(browserSession, message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: {}", getSessionId(session), exception);
        cleanUpSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed for session: {}, status: {}", getSessionId(session), status);
        cleanUpSession(session);
    }

    private void connectToSandbox(WebSocketSession browserSession, String sandboxUrl, String sessionId) {
        WebSocketHandler sandboxHandler = new BinaryWebSocketHandler() {
            private volatile WebSocketSession sandboxSession = null;

            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                this.sandboxSession = session;
                // 存储 worker 会话
                workerSessions.put(sessionId, session);
                // 建立会话映射关系
                browserToSandboxSessionIds.put(sessionId, sessionId);
                sandboxToBrowserSessionIds.put(sessionId, sessionId);
                log.info("Connected to sandbox for session: {}", sessionId);
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
                if (browserSession.isOpen()) {
                    browserSession.sendMessage(message);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable ex) {
                log.error("Sandbox WebSocket transport error for session: {}", sessionId, ex);
                closeBoth(browserSession, session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                log.info("Sandbox WebSocket connection closed for session: {}, status: {}", sessionId, status);
                closeBoth(browserSession, session);
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        // 设置子协议为 "binary"（可选，取决于沙箱是否要求）
        List<String> subProtocols = Collections.singletonList("binary");

        try {
            ListenableFuture<WebSocketSession> future = webSocketClient.doHandshake(
                    sandboxHandler,
                    new WebSocketHttpHeaders(),
                    URI.create(sandboxUrl)
            );
            // 可添加超时处理
        } catch (Exception e) {
            log.error("Failed to connect to sandbox: {}", sandboxUrl, e);
            try {
                browserSession.close(CloseStatus.SERVER_ERROR.withReason("Sandbox unreachable"));
            } catch (IOException ignored) {}
        }
    }

    private void closeBoth(WebSocketSession a, WebSocketSession b) {
        try { if (a != null && a.isOpen()) a.close(); } catch (Exception ignored) {}
        try { if (b != null && b.isOpen()) b.close(); } catch (Exception ignored) {}
        
        // 清理会话
        cleanUpSession(a);
        cleanUpSession(b);
    }

    private void cleanUpSession(WebSocketSession session) {
        if (session == null) return;
        
        String sessionId = getSessionId(session);
        if (sessionId != null) {
            frontendSessions.remove(sessionId);
            workerSessions.remove(sessionId);
            
            // 清理会话映射关系
            String sandboxSessionId = browserToSandboxSessionIds.remove(sessionId);
            if (sandboxSessionId != null) {
                sandboxToBrowserSessionIds.remove(sandboxSessionId);
            }
            
            log.info("Cleaned up session: {}", sessionId);
        }
    }

    private String extractSessionId(String path) {
        // 路径格式: /{session_id}/vnc
        String[] parts = path.split("/");
        if (parts.length >= 3 && !parts[2].isEmpty()) {
            return parts[2];
        }
        throw new IllegalArgumentException("Invalid path: " + path);
    }

    private String parseQueryParam(String query, String paramName) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && paramName.equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
    
    private String getSessionId(WebSocketSession session) {
        if (session == null) return null;
        
        // 从URI中提取sessionId
        URI uri = session.getUri();
        if (uri != null) {
            String path = uri.getPath();
            if (path != null) {
                try {
                    return extractSessionId(path);
                } catch (Exception e) {
                    log.warn("Failed to extract session ID from path: {}", path, e);
                }
            }
        }
        return null;
    }
}