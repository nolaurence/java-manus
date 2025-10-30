package cn.nolaurene.cms.service.sandbox.backend.vnc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * VNC WebSocket 代理处理器
 * 将前端的 WebSocket 连接转发到 worker 的 VNC websockify 服务
 */
@Slf4j
@Component
public class VncWebSocketHandler implements WebSocketHandler {

    @Value("${sandbox.backend.worker-vnc-url:ws://worker:5902}")
    private String workerVncUrl;

    // 存储前端连接与 worker 连接的映射
    private final Map<String, WebSocketSession> frontendSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> workerSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession frontendSession) throws Exception {
        String sessionId = frontendSession.getId();
        
        // 从查询参数中获取自定义 sessionId（如果有）
        URI uri = frontendSession.getUri();
        String customSessionId = null;
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null && query.contains("sessionId=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("sessionId=")) {
                        customSessionId = param.substring("sessionId=".length());
                        break;
                    }
                }
            }
        }
        
        String logSessionId = customSessionId != null ? customSessionId : sessionId;
        log.info("前端 VNC 连接建立: sessionId={}, customSessionId={}", sessionId, customSessionId);

        try {
            // 连接到 worker 的 VNC websockify 服务
            StandardWebSocketClient client = new StandardWebSocketClient();

            WebSocketHandler workerHandler = new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession workerSession) throws Exception {
                    log.info("成功连接到 worker VNC: sessionId={}", logSessionId);
                    workerSessions.put(sessionId, workerSession);
                }

                @Override
                public void handleMessage(WebSocketSession workerSession, WebSocketMessage<?> message) throws Exception {
                    // 将 worker 的消息转发给前端
                    if (frontendSession.isOpen()) {
                        try {
                            if (message instanceof BinaryMessage) {
                                frontendSession.sendMessage(message);
                            } else if (message instanceof TextMessage) {
                                frontendSession.sendMessage(message);
                            }
                        } catch (Exception e) {
                            log.error("转发消息到前端失败: sessionId={}", logSessionId, e);
                        }
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession workerSession, Throwable exception) throws Exception {
                    log.error("Worker VNC 连接错误: sessionId={}", logSessionId, exception);
                    closeConnections(sessionId);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession workerSession, CloseStatus closeStatus) throws Exception {
                    log.info("Worker VNC 连接关闭: sessionId={}, status={}", logSessionId, closeStatus);
                    closeConnections(sessionId);
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            };

            // 建立到 worker 的连接，增加超时设置和缓冲区大小
            // 对于1280x720分辨率的VNC画面，设置缓冲区为2MB以确保能处理完整的帧数据
            client.setUserProperties(Map.of(
                "org.apache.tomcat.websocket.IO_TIMEOUT_MS", "300000",
                "org.apache.tomcat.websocket.RECEIVE_BUFFER_SIZE", "2097152" // 设置为2MB (2*1024*1024)
            ));
            client.doHandshake(workerHandler, workerVncUrl).get(10, TimeUnit.SECONDS);
            frontendSessions.put(sessionId, frontendSession);
            
            log.info("VNC 代理连接建立成功: sessionId={}", logSessionId);

        } catch (Exception e) {
            log.error("连接到 worker VNC 失败: sessionId={}", logSessionId, e);
            if (frontendSession.isOpen()) {
                frontendSession.close(CloseStatus.SERVER_ERROR);
            }
            throw e;
        }
    }

    @Override
    public void handleMessage(WebSocketSession frontendSession, WebSocketMessage<?> message) throws Exception {
        String sessionId = frontendSession.getId();
        WebSocketSession workerSession = workerSessions.get(sessionId);

        if (workerSession != null && workerSession.isOpen()) {
            try {
                // 将前端的消息转发给 worker
                if (message instanceof BinaryMessage) {
                    workerSession.sendMessage(message);
                } else if (message instanceof TextMessage) {
                    workerSession.sendMessage(message);
                }
            } catch (Exception e) {
                log.error("转发消息到 worker 失败: sessionId={}", sessionId, e);
                throw e;
            }
        } else {
            log.warn("Worker VNC 连接不可用: sessionId={}", sessionId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession frontendSession, Throwable exception) throws Exception {
        String sessionId = frontendSession.getId();
        log.error("前端 VNC 连接错误: sessionId={}", sessionId, exception);
        closeConnections(sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession frontendSession, CloseStatus closeStatus) throws Exception {
        String sessionId = frontendSession.getId();
        log.info("前端 VNC 连接关闭: sessionId={}, status={}", sessionId, closeStatus);
        closeConnections(sessionId);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 关闭前端和 worker 的连接
     */
    private void closeConnections(String sessionId) {
        // 关闭前端连接
        WebSocketSession frontendSession = frontendSessions.remove(sessionId);
        if (frontendSession != null && frontendSession.isOpen()) {
            try {
                frontendSession.close();
            } catch (IOException e) {
                log.error("关闭前端连接失败: sessionId={}", sessionId, e);
            }
        }

        // 关闭 worker 连接
        WebSocketSession workerSession = workerSessions.remove(sessionId);
        if (workerSession != null && workerSession.isOpen()) {
            try {
                workerSession.close();
            } catch (IOException e) {
                log.error("关闭 worker 连接失败: sessionId={}", sessionId, e);
            }
        }
    }
}