package cn.nolaurene.cms.service.sandbox.worker.shell;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

@Configuration
@EnableWebSocket
public class ShellWebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ShellWebSockerHandler shellHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(shellHandler, "/worker/shell/wss")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}
