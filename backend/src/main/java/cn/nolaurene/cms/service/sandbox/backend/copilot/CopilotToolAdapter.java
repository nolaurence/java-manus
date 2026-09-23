package cn.nolaurene.cms.service.sandbox.backend.copilot;

import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.Function;

/** Bridges legacy tools and LangChain declarations to the in-process contract. */
public final class CopilotToolAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ThreadPoolExecutor TOOL_HANDLER_EXECUTOR = new ThreadPoolExecutor(
            4,
            16,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256),
            runnable -> {
                Thread thread = new Thread(runnable, "copilot-tool-handlers");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private CopilotToolAdapter() {
    }

    public static CopilotToolDefinition fromTool(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        CopilotToolHandler handler = invocation -> {
            Map<String, Object> context = new LinkedHashMap<>(invocation.getContext());
            context.put("sessionId", invocation.getSessionId());
            context.put("toolCallId", invocation.getToolCallId());
            context.put("toolName", invocation.getToolName());
            return invokeAsync(() -> tool.invoke(invocation.getArguments(), context));
        };
        return new CopilotToolDefinition(
                tool.name(), tool.description(), copySchema(tool.parametersSchema()), handler,
                tool.skipPermission(), false, false, Collections.emptyMap());
    }

    public static CopilotToolDefinition fromToolSpecification(
            ToolSpecification specification,
            Function<Map<String, Object>, ?> invoker) {
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(invoker, "invoker");
        Map<String, Object> schema = specification.parameters() == null
                ? emptyObjectSchema()
                : toJsonSchema(specification.parameters());
        CopilotToolHandler handler = invocation -> {
            return invokeAsync(() -> {
                Object result = invoker.apply(invocation.getArguments());
                if (result instanceof CompletionStage<?>) {
                    return bridge((CompletionStage<?>) result);
                }
                return CompletableFuture.completedFuture(result);
            });
        };
        return new CopilotToolDefinition(specification.name(), specification.description(), schema, handler);
    }

    /**
     * Variant for hosts that need the original model tool-call ID and session
     * metadata (for example MCP correlation and audit logging).
     */
    public static CopilotToolDefinition fromToolSpecificationWithInvocation(
            ToolSpecification specification,
            CopilotToolHandler invoker) {
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(invoker, "invoker");
        Map<String, Object> schema = specification.parameters() == null
                ? emptyObjectSchema()
                : toJsonSchema(specification.parameters());
        CopilotToolHandler handler = invocation -> {
            // Keep synchronous MCP/client adapters off the loop thread while
            // retaining the original invocation object and tool-call ID.  The
            // cancellable wrapper propagates timeout/abort interrupts to the
            // worker task instead of cancelling only a detached downstream
            // future.
            return invokeAsync(() -> invoker.handle(invocation));
        };
        return new CopilotToolDefinition(specification.name(), specification.description(), schema, handler);
    }

    public static Map<String, Object> copySchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return emptyObjectSchema();
        }
        Map<String, Object> result = MAPPER.convertValue(
                schema, new TypeReference<LinkedHashMap<String, Object>>() { });
        result.putIfAbsent("type", "object");
        result.putIfAbsent("properties", new LinkedHashMap<String, Object>());
        return result;
    }

    private static Map<String, Object> emptyObjectSchema() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", new LinkedHashMap<String, Object>());
        return result;
    }

    private static CompletableFuture<Object> invokeAsync(
            Supplier<CompletableFuture<Object>> supplier) {
        CancellableFuture result = new CancellableFuture();
        Future<?> task = TOOL_HANDLER_EXECUTOR.submit(() -> {
            result.markStarted();
            if (result.isCancellationRequested()) {
                result.completeCancellation();
                return;
            }
            try {
                CompletableFuture<Object> delegate = supplier.get();
                result.setDelegate(delegate);
                if (delegate == null) {
                    result.complete(null);
                } else {
                    delegate.whenComplete((value, error) -> {
                        if (error != null) {
                            result.completeExceptionally(error);
                        } else {
                            result.complete(value);
                        }
                    });
                }
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        result.setTask(task);
        return result;
    }

    /**
     * Execute an arbitrary Copilot handler on the same bounded, cancellable
     * executor used by legacy adapters.  The registry uses this entry point so
     * a user supplied handler cannot block the agent loop before returning its
     * future.
     */
    public static CompletableFuture<Object> invokeOnBoundedExecutor(
            Supplier<CompletableFuture<Object>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return invokeAsync(supplier);
    }

    private static CompletableFuture<Object> bridge(CompletionStage<?> stage) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        stage.whenComplete((value, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(value);
            }
        });
        return result;
    }

    /** A future whose cancellation reaches both the executor task and delegate. */
    private static final class CancellableFuture extends CompletableFuture<Object> {
        private volatile Future<?> task;
        private volatile CompletableFuture<Object> delegate;
        private volatile boolean cancellationRequested;
        private volatile boolean started;

        private boolean isCancellationRequested() {
            return cancellationRequested;
        }

        private void setTask(Future<?> value) {
            this.task = value;
            if (cancellationRequested) {
                boolean cancelled = value.cancel(true);
                if (!started && cancelled) {
                    completeCancellation();
                }
            }
        }

        private void markStarted() {
            started = true;
        }

        private void setDelegate(CompletableFuture<Object> value) {
            this.delegate = value;
            if (cancellationRequested && value != null) {
                value.cancel(true);
            }
        }

        private void completeCancellation() {
            if (!isDone()) {
                completeExceptionally(new CancellationException("tool invocation cancelled"));
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (isDone()) {
                return false;
            }
            cancellationRequested = true;
            Future<?> currentTask = task;
            if (currentTask != null) {
                currentTask.cancel(mayInterruptIfRunning);
            }
            CompletableFuture<Object> currentDelegate = delegate;
            if (currentDelegate != null) {
                currentDelegate.cancel(mayInterruptIfRunning);
            }
            // Keep this future pending until the worker observes cancellation
            // and completes, so the registry's active-handler set reflects
            // physical task quiescence rather than only logical cancellation.
            return true;
        }
    }

    public static Map<String, Object> toJsonSchema(JsonObjectSchema schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        if (schema.description() != null) {
            result.put("description", schema.description());
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        if (schema.properties() != null) {
            schema.properties().forEach((name, child) -> properties.put(name, toJsonSchema(child)));
        }
        result.put("properties", properties);
        if (schema.required() != null && !schema.required().isEmpty()) {
            result.put("required", new ArrayList<>(schema.required()));
        }
        if (schema.additionalProperties() != null) {
            result.put("additionalProperties", schema.additionalProperties());
        }
        return result;
    }

    /** Convert a standard object schema to LangChain4j's request schema model. */
    @SuppressWarnings("unchecked")
    public static JsonObjectSchema toLangChainObjectSchema(Map<String, Object> schema) {
        Map<String, Object> safe = schema == null ? emptyObjectSchema() : schema;
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        Object description = safe.get("description");
        if (description != null) {
            builder.description(String.valueOf(description));
        }
        Object properties = safe.get("properties");
        if (properties instanceof Map<?, ?>) {
            Map<String, Object> propertyMap = new LinkedHashMap<>();
            ((Map<?, ?>) properties).forEach((key, value) -> propertyMap.put(String.valueOf(key), value));
            for (Map.Entry<String, Object> entry : propertyMap.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?>) {
                    builder.addProperty(entry.getKey(), toLangChainElement((Map<String, Object>) entry.getValue()));
                }
            }
        }
        Object required = safe.get("required");
        if (required instanceof List<?>) {
            builder.required(((List<?>) required).stream().map(String::valueOf).toArray(String[]::new));
        }
        if (safe.containsKey("additionalProperties")) {
            Object value = safe.get("additionalProperties");
            if (value instanceof Boolean) {
                builder.additionalProperties((Boolean) value);
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static JsonSchemaElement toLangChainElement(Map<String, Object> schema) {
        String type = schema.get("type") == null ? "object" : String.valueOf(schema.get("type"));
        String description = schema.get("description") == null ? null : String.valueOf(schema.get("description"));
        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?>) {
            JsonEnumSchema.Builder enumeration = JsonEnumSchema.builder()
                    .enumValues(((List<?>) enumValues).stream().map(String::valueOf).toArray(String[]::new));
            if (description != null) enumeration.description(description);
            return enumeration.build();
        }
        Object anyOf = schema.get("anyOf");
        if (anyOf instanceof List<?>) {
            List<JsonSchemaElement> options = new ArrayList<>();
            for (Object option : (List<?>) anyOf) {
                if (option instanceof Map<?, ?>) {
                    options.add(toLangChainElement((Map<String, Object>) option));
                }
            }
            JsonAnyOfSchema.Builder union = JsonAnyOfSchema.builder().anyOf(options);
            if (description != null) union.description(description);
            return union.build();
        }
        if (schema.get("$ref") != null) {
            JsonReferenceSchema.Builder reference = JsonReferenceSchema.builder()
                    .reference(String.valueOf(schema.get("$ref")));
            return reference.build();
        }
        switch (type) {
            case "string":
                return description == null ? JsonStringSchema.builder().build()
                        : JsonStringSchema.builder().description(description).build();
            case "integer":
                return description == null ? JsonIntegerSchema.builder().build()
                        : JsonIntegerSchema.builder().description(description).build();
            case "number":
                return description == null ? JsonNumberSchema.builder().build()
                        : JsonNumberSchema.builder().description(description).build();
            case "boolean":
                return description == null ? JsonBooleanSchema.builder().build()
                        : JsonBooleanSchema.builder().description(description).build();
            case "null":
                return new JsonNullSchema();
            case "array":
                Object items = schema.get("items");
                JsonArraySchema.Builder array = JsonArraySchema.builder();
                if (items instanceof Map<?, ?>) {
                    array.items(toLangChainElement((Map<String, Object>) items));
                }
                if (description != null) array.description(description);
                return array.build();
            case "object":
                return toLangChainObjectSchema(schema);
            default:
                // Preserve uncommon JSON Schema constructs without inventing a
                // lossy primitive type.
                try {
                    return JsonRawSchema.from(MAPPER.writeValueAsString(schema));
                } catch (Exception error) {
                    return JsonObjectSchema.builder().build();
                }
        }
    }

    private static Map<String, Object> toJsonSchema(JsonSchemaElement element) {
        if (element == null) {
            return new LinkedHashMap<>();
        }
        if (element instanceof JsonObjectSchema) {
            return toJsonSchema((JsonObjectSchema) element);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (element.description() != null) {
            result.put("description", element.description());
        }
        if (element instanceof JsonStringSchema) {
            result.put("type", "string");
        } else if (element instanceof JsonIntegerSchema) {
            result.put("type", "integer");
        } else if (element instanceof JsonNumberSchema) {
            result.put("type", "number");
        } else if (element instanceof JsonBooleanSchema) {
            result.put("type", "boolean");
        } else if (element instanceof JsonNullSchema) {
            result.put("type", "null");
        } else if (element instanceof JsonEnumSchema) {
            result.put("type", "string");
            result.put("enum", new ArrayList<>(((JsonEnumSchema) element).enumValues()));
        } else if (element instanceof JsonArraySchema) {
            result.put("type", "array");
            result.put("items", toJsonSchema(((JsonArraySchema) element).items()));
        } else if (element instanceof JsonAnyOfSchema) {
            List<Map<String, Object>> options = new ArrayList<>();
            for (JsonSchemaElement option : ((JsonAnyOfSchema) element).anyOf()) {
                options.add(toJsonSchema(option));
            }
            result.put("anyOf", options);
        } else if (element instanceof JsonReferenceSchema) {
            result.put("$ref", ((JsonReferenceSchema) element).reference());
        } else if (element instanceof JsonRawSchema) {
            try {
                result.putAll(MAPPER.readValue(((JsonRawSchema) element).schema(),
                        new TypeReference<LinkedHashMap<String, Object>>() { }));
            } catch (Exception ignored) {
                result.put("type", "object");
            }
        } else {
            result.put("type", "object");
        }
        return result;
    }
}
