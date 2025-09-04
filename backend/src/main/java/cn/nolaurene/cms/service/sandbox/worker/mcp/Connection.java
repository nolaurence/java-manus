package cn.nolaurene.cms.service.sandbox.worker.mcp;


import cn.nolaurene.cms.service.sandbox.worker.mcp.context.FullConfig;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalStateType;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.Tool;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.ToolActionResult;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.ToolCapability;
import com.alibaba.fastjson.JSON;
import com.microsoft.playwright.BrowserContext;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author nolaurence
 * @date 2025/7/7 下午5:36
 * @description:
 */
@Slf4j
@Service
public class Connection {

    private McpSyncServer server;

    @Getter
    private Context context;

    private List<Tool> tools;

    private Map<String, Class> toolInputParamMap;

    @Resource
    HttpServletSseServerTransportProvider transportProvider;

    public void close() {
        server.close();
        context.close();
    }

    public void createConnection(FullConfig config, BrowserContext browserContext) {
        long startTime = System.currentTimeMillis();
        List<Tool> allTools = new Tools().getSnapshotTools();
        tools = allTools.stream()
                .filter(tool -> null != tool.getCapability() || tool.getCapability().equals(ToolCapability.CORE)).
                collect(Collectors.toList());
        toolInputParamMap = getToolInputSchema(tools);
        context = new Context(tools, config, browserContext);

        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("Playwright Java Server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(false, true)  // Enable resouces support
                        .tools(true)   // Enable tools support
                        .prompts(true)
                        .logging()
                        .build())
                .build();

        this.server = syncServer;

        // register all tools
        for (Tool tool : tools) {
            McpSchema.Tool toolSchema = getMcpSdkToolSchema(tool);
            syncServer.addTool(new McpServerFeatures.SyncToolSpecification(
                    toolSchema,
                    (exchange, arguments) -> {
                        arguments.put("name", tool.getSchema().getName());
                        return handleToolCall(exchange, arguments);
                    }
            ));
        }
        log.info("start mcp server cost: {} ms", System.currentTimeMillis() - startTime);
    }

    private McpSchema.CallToolResult handleToolCall(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        log.info("[TOOL CALL] name: {}, exchange: {}, arguments: {}", arguments.get("name"), exchange, arguments);
        McpSchema.CallToolResult errorResult = new McpSchema.CallToolResult();
        errorResult.setIsError(true);
//        McpSchema.CallToolRequest request = JSON.parseObject(JSON.toJSONString(arguments), McpSchema.CallToolRequest.class);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(arguments.get("name").toString(), arguments);
        Tool tool = tools.stream().filter(t -> t.getSchema().getName().equals(request.getName())).findFirst().orElse(null);

        if (null == tool) {
            errorResult.setContent(List.of(new McpSchema.TextContent(String.format("Tool \"%s\" not found", request.getName()))));
            return errorResult;
        }

        List<ModalStateType> modalStates = context.modalStates().stream().map(state -> state.getType()).collect(Collectors.toList());
        if (null != tool.getClearsModalState() && modalStates.contains(tool.getClearsModalState())) {
            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("The tool " + request.getName() + " can only be used when there is related modal state present."));
            contents.addAll(context.modalStatesMarkdown().stream().map(McpSchema.TextContent::new).collect(Collectors.toList()));
            errorResult.setContent(contents);
            return errorResult;
        }
        if (null != tool.getClearsModalState() && CollectionUtils.isNotEmpty(modalStates)) {
            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("Tool " + request.getName() + " does not handle the modal state."));
            contents.addAll(context.modalStatesMarkdown().stream().map(McpSchema.TextContent::new).collect(Collectors.toList()));
            errorResult.setContent(contents);
            return errorResult;
        }

        // remove name in request
        request.getArguments().remove("name");
        try {
            ToolActionResult toolActionResult = context.run(tool, request.getArguments(), toolInputParamMap.get(request.getName()));
            return new McpSchema.CallToolResult(toolActionResult.getContent(), false);
        } catch (Exception e) {
            errorResult.setContent(List.of(new McpSchema.TextContent(JSON.toJSONString(e))));
            return errorResult;
        }
    }

    public McpSchema.Tool getMcpSdkToolSchema(Tool customTool) {

        // add inspection to watch NPE
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


        /**
         * newer mcp sdk support annotations filed:
         * annotations: {
         *           title: tool.schema.title,
         *           readOnlyHint: tool.schema.type === 'readOnly',
         *           destructiveHint: tool.schema.type === 'destructive',
         *           openWorldHint: true,
         *         }
         */
    }

    private Map<String, Class> getToolInputSchema(List<Tool> tools) {
        return tools.stream()
                .collect(Collectors.toMap(
                        tool -> tool.getSchema().getName(),
                        tool -> tool.getSchema().getInputSchema() != null ? tool.getSchema().getInputSchema().getClass() : Object.class
                ));
    }
}
