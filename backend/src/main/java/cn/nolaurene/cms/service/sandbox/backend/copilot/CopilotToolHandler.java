package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.util.concurrent.CompletableFuture;

/** Async handler contract used by the Java implementation of the Copilot tool protocol. */
@FunctionalInterface
public interface CopilotToolHandler {

    CompletableFuture<Object> handle(CopilotToolInvocation invocation);
}
