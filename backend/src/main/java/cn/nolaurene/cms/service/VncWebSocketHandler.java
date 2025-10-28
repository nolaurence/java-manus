package cn.nolaurene.cms.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import cn.nolaurene.cms.service.sandbox.backend.session.GlobalAgentSessionManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class VncWebSocketHandler extends AbstractWebSocketHandler {
    
    private static final String SANDBOX_HOST = "http://worker";

    private static final int VNC_PORT = 5902;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Frontend connected via WebSocket");

        // 创建到沙箱 x11vnc 的 TCP 连接
        SocketChannel vncChannel = SocketChannel.open();
        vncChannel.connect(new InetSocketAddress(SANDBOX_HOST, VNC_PORT));
        vncChannel.configureBlocking(false); // 非阻塞（可选，但需配合 NIO）

        // 将 vncChannel 绑定到 session 的 attributes，用于后续通信
        session.getAttributes().put("vncChannel", vncChannel);

        // 启动一个线程从 VNC 读取数据并转发给前端
        startVncToWebSocketForwarder(session, vncChannel);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // 前端 → 沙箱
        SocketChannel vncChannel = (SocketChannel) session.getAttributes().get("vncChannel");
        if (vncChannel != null && vncChannel.isConnected()) {
            vncChannel.write(message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("Frontend disconnected: " + status);
        SocketChannel vncChannel = (SocketChannel) session.getAttributes().get("vncChannel");
        if (vncChannel != null) {
            try {
                vncChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket error: " + exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    // 从 VNC 读取数据并发送给前端
    private void startVncToWebSocketForwarder(WebSocketSession session, SocketChannel vncChannel) {
        Thread forwarder = new Thread(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            try {
                while (session.isOpen() && vncChannel.isConnected()) {
                    int bytesRead = vncChannel.read(buffer);
                    if (bytesRead > 0) {
                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        buffer.clear();

                        // 发送给前端（必须是 BinaryMessage）
                        session.sendMessage(new BinaryMessage(data));
                    } else if (bytesRead == -1) {
                        break; // 连接关闭
                    }
                    // 短暂休眠避免 CPU 占用过高（可选）
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    session.close(CloseStatus.GOING_AWAY);
                } catch (IOException ignored) {}
            }
        });
        forwarder.setDaemon(true);
        forwarder.start();
    }
}
