package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Data
public class ToolResult {

    private final List<String> code;
    private final Supplier<CompletableFuture<ToolActionResult>> action;
    private final boolean captureSnapshot;
    private final boolean waitForNetwork;
    private final ToolActionResult resultOverride;

    private ToolResult(Builder builder) {
        this.code = builder.code;
        this.action = builder.action;
        this.captureSnapshot = builder.captureSnapshot;
        this.waitForNetwork = builder.waitForNetwork;
        this.resultOverride = builder.resultOverride;
    }

    public boolean hasCaptureSnapshot() {
        return captureSnapshot;
    }

    public boolean hasWaitForNetwork() {
        return waitForNetwork;
    }

    public boolean hasAction() {
        return action != null;
    }

    public boolean hasResultOverride() {
        return resultOverride != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> code;
        private Supplier<CompletableFuture<ToolActionResult>> action;
        private boolean captureSnapshot = false;
        private boolean waitForNetwork = false;
        private ToolActionResult resultOverride;

        public Builder code(List<String> code) {
            this.code = code;
            return this;
        }

        public Builder action(Supplier<CompletableFuture<ToolActionResult>> action) {
            this.action = action;
            return this;
        }

        public Builder captureSnapshot(boolean captureSnapshot) {
            this.captureSnapshot = captureSnapshot;
            return this;
        }

        public Builder waitForNetwork(boolean waitForNetwork) {
            this.waitForNetwork = waitForNetwork;
            return this;
        }

        public Builder resultOverride(ToolActionResult resultOverride) {
            this.resultOverride = resultOverride;
            return this;
        }

        public ToolResult build() {
            if (code == null) {
                throw new IllegalStateException("code is required");
            }
            return new ToolResult(this);
        }
    }
}
