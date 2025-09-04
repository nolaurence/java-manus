package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ToolActionResult {

    /**
     * { content?: (ImageContent | TextContent)[] }
     * mcp sdk 中的类型，未升级17时先用Object代替
     */
    private List<McpSchema.Content> content;

    public ToolActionResult(McpSchema.Content content) {
        this.content = List.of(content);
    }

    public ToolActionResult(List<McpSchema.Content> content) {
        this.content = content;
    }

    public static ToolActionResult empty() {
        return new ToolActionResult(List.of());
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    @Override
    public String toString() {
        return "ToolActionResult{" +
                "content=" + JSON.toJSONString(content) +
                '}';
    }
}
