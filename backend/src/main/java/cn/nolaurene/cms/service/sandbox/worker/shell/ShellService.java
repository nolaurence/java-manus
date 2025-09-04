package cn.nolaurene.cms.service.sandbox.worker.shell;

import cn.nolaurene.cms.common.sandbox.worker.resp.shell.*;
import cn.nolaurene.cms.exception.manus.AppException;
import cn.nolaurene.cms.exception.manus.BadRequestException;
import cn.nolaurene.cms.exception.manus.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author nolaurence
 * @date 2025/5/13 19:08
 * @description: shell ops old api
 */
@Slf4j
@Service
public class ShellService {

    private static final int EXECUTION_TIMEOUT = 5; // seconds

    private final Map<String, SessionData> activeShells = new ConcurrentHashMap<>();
    private final Map<String, Process> shellProcesses = new ConcurrentHashMap<>();

    private ExecutorService executor = Executors.newFixedThreadPool(10);

    public String createSessionId() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session ID: {}", sessionId);
        return sessionId;
    }

    private String getDisplayPath(String path) {
        String homeDir = System.getProperty("user.home");
        log.info("Home directory:{}, path:{}", homeDir, path);
        if (path.startsWith(homeDir)) {
            return path.replace(homeDir, "");
        }
        return path;
    }

    // 格式化命令提示符
    private String formatPs1(String execDir) {
        String username = System.getProperty("user.name");
        String hostname = "localhost"; // Java获取主机名较复杂
        String displayDir = getDisplayPath(execDir);
        return String.format("%s@%s:%s $", username, hostname, displayDir);
    }

    private void startOutputReader(String sessionId, Process process) {
        SessionData session = activeShells.get(sessionId);
        if (session == null) {
            return;
        }

        executor.submit(() -> {
            try (InputStream inputStream = process.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[128];
                StringBuilder output = new StringBuilder();

                int charsRead;
                while ((charsRead = reader.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, charsRead);
                    output.append(chunk);

                    // 更新会话状态
                    session.setOutput(output.toString());
                    if (!session.getConsole().isEmpty()) {
                        ConsoleRecord last = session.getConsole().get(session.getConsole().size() - 1);
                        last.setOutput(last.getOutput() + chunk);
                    }
                }
            } catch (IOException e) {
                log.error("Error reading process output: {}", e.getMessage(), e);
            } finally {
                log.info("Output reader for session {} has finished.", sessionId);
            }
        });
    }

    // 创建新进程
    private Process createProcess(String command, String execDir) throws IOException {
        log.info("Creating process for command: {} in directory: {}", command, execDir);

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.directory(new File(execDir));
        pb.redirectErrorStream(true); // 合并错误流到标准输出

        Process process = pb.start();
        shellProcesses.put(command, process); // 可能需要更合适的键
        return process;
    }

    // 执行命令
    public ShellCommandResult execCommand(String sessionId, String execDir, String command) throws Exception {
        log.info("Executing command in session{}: {}", sessionId, command);

        if (StringUtils.isBlank(execDir)) {
            execDir = System.getProperty("user.home");
        }

        if (!new File(execDir).exists()) {
            log.error("Directory does not exist: {}", execDir);
            throw new BadRequestException("Directory does not exist: " + execDir);
        }

        try {
            String ps1 = formatPs1(execDir);

            // 新建会话
            if (!activeShells.containsKey(sessionId)) {
                log.info("Creating new shell session: {}", sessionId);
                Process process = createProcess(command, execDir);
                SessionData sessionData = new SessionData(
                        process,
                        execDir,
                        "",
                        new ArrayList<>(Collections.singletonList(new ConsoleRecord(ps1, command, "")))
                );
                activeShells.put(sessionId, sessionData);
                startOutputReader(sessionId, process);
            } else {
                // 复用现有会话
                SessionData session = activeShells.get(sessionId);
                Process oldProcess = session.getProcess();

                if (oldProcess.isAlive()) {
                    log.info("Terminating previous process in session: {}", sessionId);
                    oldProcess.destroy();
                    if (!oldProcess.waitFor(1, TimeUnit.SECONDS)) {
                        log.warn("ForceFully killing process in session: {}", sessionId);
                        oldProcess.destroyForcibly();
                    }
                }

                Process newProcess = createProcess(command, execDir);
                session.setProcess(newProcess);
                session.setExecDir(execDir);
                session.setOutput(""); // 清楚旧输出

                ConsoleRecord newRecord = new ConsoleRecord(ps1, command, "");
                session.getConsole().add(newRecord);
                startOutputReader(sessionId, newProcess);
            }

            // 等待执行结果
            try {
                if (waitForProcess(sessionId, EXECUTION_TIMEOUT)) {
                    ShellViewResult viewResult = viewShell(sessionId);
                    SessionData session = activeShells.get(sessionId);

                    if (!session.getConsole().isEmpty()) {
                        ConsoleRecord last = session.getConsole().get(session.getConsole().size() - 1);
                        last.setOutput(viewResult.getOutput());
                    }

                    return new ShellCommandResult(
                            sessionId,
                            command,
                            "completed",
                            session.getProcess().exitValue(),
                            viewResult.getOutput(),
                            getConsoleRecords(sessionId)
                    );
                }
            } catch (BadRequestException e) {
                log.debug("Process still running after timeout in session: {}", sessionId);
            }

            // 返回运行中状态
            return new ShellCommandResult(
                    sessionId,
                    command,
                    "running",
                    0,
                    null,
                    getConsoleRecords(sessionId)
            );
        } catch (RuntimeException e) {
            log.error("Command execution failed: {}", e.getMessage(), e);
            throw new AppException("Command execution failed: " + e.getMessage());
        }
    }

    // 查看会话内容
    public ShellViewResult viewShell(String sessionId) {
        log.debug("Viewing shell content for session: {}", sessionId);

        SessionData session = activeShells.get(sessionId);
        if (session == null) {
            log.error("Session ID not found: {}", sessionId);
            throw new ResourceNotFoundException("Session ID does not exist: " + sessionId);
        }

        return new ShellViewResult(
                session.getOutput(),
                sessionId,
                session.getConsole()
        );
    }

    // 获取控制台记录
    public List<ConsoleRecord> getConsoleRecords(String sessionId) {
        log.debug("Getting console records for session: {}", sessionId);

        SessionData session = activeShells.get(sessionId);
        if (session == null) {
            log.error("Session ID not found: {}", sessionId);
            throw new ResourceNotFoundException("Session ID does not exist: " + sessionId);
        }

        return session.getConsole();
    }

    // 等待进程完成
    public boolean waitForProcess(String sessionId, int seconds) throws Exception {
        log.info("Waiting for process in session: {}, timeout: {}s", sessionId, seconds);

        SessionData session = activeShells.get(sessionId);
        if (session == null) {
            log.error("Session ID not found: {}", sessionId);
            throw new ResourceNotFoundException("Session ID does not exist: " + sessionId);
        }

        return session.getProcess().waitFor(seconds, TimeUnit.SECONDS);
    }

    // 写入输入
    public ShellWriteResult writeToProcess(String sessionId, String inputText, boolean pressEnter) throws Exception {
        log.debug("Writting to process in session: {}. press enter: {}", sessionId, pressEnter);

        SessionData session = activeShells.get(sessionId);
        if (session == null) {
            log.error("Session ID not found: {}", sessionId);
            throw new ResourceNotFoundException("Session ID does not exist: " + sessionId);
        }

        Process process = session.getProcess();
        if (!process.isAlive()) {
            log.error("Process has already terminated");
            throw new BadRequestException("Process has ended, cannot write input");
        }

        String input = pressEnter ? inputText + "\n" : inputText;
        OutputStream stdin = process.getOutputStream();
        stdin.write(input.getBytes(StandardCharsets.UTF_8));
        stdin.flush();

        // 更新输出记录
        session.setOutput(session.getOutput() + input);
        if (!session.getConsole().isEmpty()) {
            ConsoleRecord last = session.getConsole().get(session.getConsole().size() - 1);
            last.setOutput(last.getOutput() + input);
        }

        return new ShellWriteResult("success");
    }

    // 终止进程
    public ShellKillResult killProcess(String sessionId) throws Exception {
        log.info("Killing process in session: {}", sessionId);

        SessionData session = activeShells.get(sessionId);
        if (session == null) {
            log.error("Session ID not found: {}", sessionId);
            throw new ResourceNotFoundException("Session ID does not exist: " + sessionId);
        }

        Process process = session.getProcess();

        if (process.isAlive()) {
            log.debug("Attempting to terminate process gracefully");
            process.destroy();

            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                log.warn("Forcefully killing the process");
                process.destroyForcibly();
                process.waitFor();
            }

            return new ShellKillResult("terminated", process.exitValue());
        } else {
            return new ShellKillResult("already_terminated", process.exitValue());
        }
    }
}
