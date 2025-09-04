package cn.nolaurene.cms.service.sandbox.worker.shell;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;

import java.io.*;

@Slf4j
@Service
public class ShellWebSockerHandler implements WebSocketHandler {

    private Process process;
    private OutputStreamWriter stdinWriter;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh");
        pb.directory(new File(System.getProperty("user.dir")));
        process = pb.start();

        stdinWriter = new OutputStreamWriter(process.getOutputStream());

        // 读取输出
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    session.sendMessage(new TextMessage(line + "\r\n"));
                }
            } catch (Exception e) {
                log.error("[ShellWebSocketHandler] Error reading process output", e);
            }
        }).start();
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            stdinWriter.write(payload);
            stdinWriter.flush();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (stdinWriter != null) {
            try { stdinWriter.close(); } catch (IOException e) {}
        }
        if (process != null) process.destroyForcibly();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[ShellWebSocketHandler] Transport error", exception);
    }

    @Override
    public boolean supportsPartialMessages() { return false; }
}
