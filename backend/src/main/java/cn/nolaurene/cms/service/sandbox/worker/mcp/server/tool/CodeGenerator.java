package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import cn.nolaurene.cms.service.sandbox.worker.mcp.Context;

@FunctionalInterface
public interface CodeGenerator<T> {
    /**
     * 生成代码
     *
     * @param context 执行上下文
     * @param params 已验证的参数
     * @return 生成的代码字符串
     */
    String generateCode(Context context, T params);
}
