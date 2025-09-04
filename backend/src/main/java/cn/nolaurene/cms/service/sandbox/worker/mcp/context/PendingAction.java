package cn.nolaurene.cms.service.sandbox.worker.mcp.context;

import lombok.Data;

import java.util.concurrent.CompletableFuture;

@Data
public class PendingAction {

    private CompletableFuture<Void> dialogShown;
}
