package cn.nolaurene.cms.common.sandbox.worker.req.mcp;


import lombok.Data;

/**
 * @author nolau
 * @date 2025/6/15
 * @description MCP命令请求体
 */
@Data
public class MCPCommand {

    private String action; // 命令动作，如"start", "stop", "status"等

    private String selector;  // CSS or text selector

    private String value;  // fill value or URL
}
