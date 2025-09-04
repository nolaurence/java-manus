package cn.nolaurene.cms.controller.sandbox.worker;

import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.service.sandbox.worker.mcp.Context;
import cn.nolaurene.cms.service.sandbox.worker.mcp.McpServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/worker/mcp")
@Tag(name = "sandbox worker - mcp server manage interface")
public class PlaywrightMcpController {

    @Resource
    private McpServer mcpServer;

    @GetMapping("/start/{sessionId}")
    @Operation(summary = "Start MCP server for a given session ID")
    public Response<Boolean> startMcpServer(@PathVariable String sessionId) {
        Context context = mcpServer.startSession(sessionId);
        if (null != context) {
            return Response.success(true);
        } else {
            return Response.error("Failed to start MCP server for session: " + sessionId, null);
        }
    }

    @GetMapping("/stop/{sessionId}")
    @Operation(summary = "Stop MCP server for a given session ID")
    public Response<Boolean> stopMcpServer(@PathVariable String sessionId) {
        try {
            mcpServer.stopSession();
            return Response.success(true);
        } catch (Exception e) {
            return Response.error("Failed to stop MCP server for session: " + sessionId, null);
        }
    }
}
