package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import cn.nolaurene.cms.service.sandbox.worker.mcp.Context;

/**
 * 工具执行器函数式接口
 *
 * 替代 TypeScript 中的 handle 函数，提供函数式编程支持
 */
@FunctionalInterface
public interface ToolHandler<T> {
    /**
     * 执行工具逻辑
     *
     * @param context 执行上下文
     * @param params 已验证的参数
     * @return 执行结果
     */
    ToolResult execute(Context context, T params);
}
