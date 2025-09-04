package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;


/**
 * @author nolaurence
 * @date 2025/7/8 上午10:47
 * @description:
 */
@FunctionalInterface
public interface ToolFactory {
    /**
     * Creates a tool instance with the specified snapshot configuration.
     *
     * @param snapshot Whether snapshot functionality should be enabled
     * @return A new Tool instance
     */
    Tool<?> createTool(boolean snapshot);
}
