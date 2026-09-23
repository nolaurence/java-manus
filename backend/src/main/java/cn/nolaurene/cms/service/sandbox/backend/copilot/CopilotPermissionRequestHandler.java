package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.util.concurrent.CompletionStage;

/** Async permission hook for interactive Copilot-style hosts. */
@FunctionalInterface
public interface CopilotPermissionRequestHandler {

    /**
     * Return a future decision.  The request's own future is useful for hosts
     * that expose a pending-request queue and resolve it later.
     */
    CompletionStage<CopilotPermissionDecision> request(CopilotPermissionRequest request);
}
