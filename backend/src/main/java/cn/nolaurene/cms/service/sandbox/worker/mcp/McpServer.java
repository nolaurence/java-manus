package cn.nolaurene.cms.service.sandbox.worker.mcp;

import cn.nolaurene.cms.service.sandbox.worker.browser.BrowserService;
import cn.nolaurene.cms.service.sandbox.worker.mcp.context.FullConfig;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.Tool;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.ToolActionResult;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.ToolResult;
import cn.nolaurene.cms.service.sandbox.worker.shell.tools.ShellExecTool;
import com.alibaba.fastjson.JSON;
import com.microsoft.playwright.BrowserContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class McpServer {

    @Value("${maintenance.env}")
    private String env;

    @Value("${sandbox.worker.logDir}")
    private String logDir;

    @Value("${sandbox.worker.mcp-port}")
    private String MCP_SERVER_PORT;

    @Value("${sandbox.worker.playwright-mcp-version}")
    private String PLAYWRIGHT_MCP_VERSION;

    private McpSyncServer server;

    @Resource
    private BrowserService browserService;

    @Resource
    HttpServletSseServerTransportProvider transportProvider;

    @Resource
    private Connection connection;

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            5,
            20,
            0L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>()
    );

    @PostConstruct
    public void init() {
        if (!"worker".equalsIgnoreCase(env)) {
            log.info("MCP Server init skipped for type: {}", env);
            return;
        }
        // 初始化MCP服务器
        log.info("Initializing MCP Server...");

        try {
            // 启动浏览器服务
            browserService.startBrowser();
            log.info("Browser service started successfully.");

            String mcpLogDir = logDir + "/mcp";
            // make sure the directory exists
            new File(mcpLogDir).mkdirs();

            ProcessBuilder pb = new ProcessBuilder(
                    "npx", "@playwright/mcp@" + PLAYWRIGHT_MCP_VERSION,
                    "--browser", "chrome",
                    "--caps", "pdf",
                    "--cdp-endpoint", "http://127.0.0.1:8222",
                    "--output-dir", mcpLogDir,
                    "--user-data-dir", mcpLogDir,
                    "--port", MCP_SERVER_PORT,
                    "--viewport-size", "1280,1024"
            );

            Process process = pb.start();

            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            EXECUTOR.submit(() -> {
                try {
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                        log.info("[MCP STDOUT]: {}", line);
                    }
                } catch (IOException e) {
                    log.error("[MCP STDOUT] Exception: {}", e.getMessage());
                }
            });
            EXECUTOR.submit(() -> {
                try {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        log.info("[MCP STDERR]: {}", line);
                    }
                } catch (IOException e) {
                    log.error("[MCP STDERR] Exception: {}", e.getMessage());
                }
            });

            McpSyncServer syncServer = io.modelcontextprotocol.server.McpServer.sync(transportProvider)
                    .serverInfo("Linux Shell and file operation Server", "1.0.0")
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .resources(false, true)  // Enable resouces support
                            .tools(true)   // Enable tools support
                            .prompts(true)
                            .logging()
                            .build())
                    .build();

            this.server = syncServer;

            // 注册工具
            addShellTool();
            log.info("Vanilla Tool mcp server start successfully on port 7002");

            // TODO: 文件操作工具

        } catch (Exception e) {
            log.error("Failed to start browser service: {}", e.getMessage(), e);
        }

        /**
         * npx @playwright/mcp@latest --browser chrome --caps pdf --cdp-endpoint http://127.0.0.1:9222 --output-dir <path> --port 7500 --user-data-dir <path> --viewport-size 1280,1024
         */
    }

    private McpSchema.Tool getMcpSdkToolSchema(Tool customTool) {
        try {
            McpSchema.Tool tool =  new McpSchema.Tool(
                    customTool.getSchema().getName(),
                    customTool.getSchema().getDescription(),
                    customTool.getSchema().describeSchema());
            return tool;
        } catch (Exception e) {
            log.error("Failed to describe the tool {}", JSON.toJSONString(customTool), e);
            throw new RuntimeException(e);
        }
    }

    private void addShellTool() {
        Tool shellTool = ShellExecTool.getAllTools(false).get(0);
        McpSchema.Tool toolSchema = getMcpSdkToolSchema(shellTool);

        this.server.addTool(new McpServerFeatures.SyncToolSpecification(
                toolSchema,
                (exchange, arguments) -> {
                    // parse arguments
                    Object shellExecInput = JSON.parseObject(JSON.toJSONString(arguments), shellTool.getSchema().getInputSchema().getClass());
                    ToolResult executeResult = shellTool.getHandler().execute(null, shellExecInput);
                    ToolActionResult toolActionResult = executeResult.getAction().get().join();
                    return new McpSchema.CallToolResult(toolActionResult.getContent(), false);
                }
        ));
    }

    public Context startSession(String sessionId) {
        // create new mcp session
        log.info("Starting MCP session with ID: {}", sessionId);
        BrowserContext browserContext = browserService.createNewContext(sessionId);

        // create new mcp context for playwright mcp
        FullConfig fullConfig = new FullConfig();
        fullConfig.setOutputDir(logDir + "/mcpserver/" + sessionId);
        connection.createConnection(fullConfig, browserContext);

        log.info("MCP server started for session: {}", sessionId);

        return connection.getContext();
    }

    public void stopSession() {
        connection.close();
    }
}
