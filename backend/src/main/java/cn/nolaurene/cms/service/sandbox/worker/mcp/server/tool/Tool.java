package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalStateType;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents a tool that can be executed with specific capabilities and configuration.
 * This is the Java equivalent of the TypeScript Tool interface.
 *
 * @param <T> The type of input this tool accepts
 */
@Getter
public class Tool<T> {
    private final ToolCapability capability;
    private final ToolSchema<T> schema;
    private final ModalStateType clearsModalState;
    private final ToolHandler<T> handler;

    private Tool(Builder<T> builder) {
        this.capability = builder.capability;
        this.schema = builder.schema;
        this.clearsModalState = builder.clearsModalState;
        this.handler = builder.handler;
    }

    /**
     * Creates a new Tool builder.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder class for creating Tool instances.
     * This replaces the TypeScript defineTool function with a proper Java builder pattern.
     */
    public static class Builder<T> {
        private ToolCapability capability;
        private ToolSchema<T> schema;
        private ModalStateType clearsModalState;
        private ToolHandler<T> handler;

        public Builder<T> capability(ToolCapability capability) {
            this.capability = capability;
            return this;
        }

        public Builder<T> schema(ToolSchema<T> schema) {
            this.schema = schema;
            return this;
        }

        public Builder<T> clearsModalState(ModalStateType clearsModalState) {
            this.clearsModalState = clearsModalState;
            return this;
        }

        public Builder<T> handler(ToolHandler<T> handler) {
            this.handler = handler;
            return this;
        }

        public Tool<T> build() {
            Objects.requireNonNull(capability, "capability is required");
            Objects.requireNonNull(schema, "schema is required");
            Objects.requireNonNull(handler, "handler is required");

            return new Tool<>(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Tool<?> tool = (Tool<?>) obj;
        return capability == tool.capability &&
                Objects.equals(schema, tool.schema) &&
                clearsModalState == tool.clearsModalState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(capability, schema, clearsModalState);
    }

    @Override
    public String toString() {
        return "Tool{" +
                "capability=" + capability +
                ", schema=" + schema +
                ", clearsModalState=" + clearsModalState +
                '}';
    }
}
