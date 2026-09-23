package cn.nolaurene.cms.service.sandbox.backend.copilot;

/** Permission callback invoked before a tool handler runs. */
@FunctionalInterface
public interface CopilotPermissionHandler {

    CopilotPermissionDecision check(CopilotToolInvocation invocation,
                                    CopilotToolDefinition definition);
}
