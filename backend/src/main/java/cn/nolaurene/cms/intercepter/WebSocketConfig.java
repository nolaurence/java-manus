package cn.nolaurene.cms.intercepter;

import javax.annotation.Resource;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import cn.nolaurene.cms.service.VncWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer{
    
    @Resource
    private VncWebSocketHandler vncWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 允许跨域（根据需要调整）
        registry.addHandler(vncWebSocketHandler, "/vnc")
                .setAllowedOrigins("*"); // 生产环境应限制来源
    }
}
