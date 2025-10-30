package cn.nolaurene.cms.service.sandbox.backend.vnc;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * VNC WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class VncWebSocketConfig implements WebSocketConfigurer {

    @Resource
    private VncWebSocketHandler vncWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(vncWebSocketHandler, "/vnc")
                .setAllowedOrigins("*");
    }
}
