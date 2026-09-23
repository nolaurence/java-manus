package cn.nolaurene.cms.service.sandbox.backend.copilot;

import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.UUID;

/** Thread-safe deterministic registry for Copilot-compatible tools. */
public final class CopilotToolRegistry {

    private final Map<String, CopilotToolDefinition> definitions = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Object>> activeHandlers = ConcurrentHashMap.newKeySet();
    private final Map<CompletableFuture<CopilotToolExecution>, String> activeExecutions =
            new ConcurrentHashMap<>();
    private final Map<String, CopilotPermissionRequest> pendingPermissionRequests = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<CopilotToolExecution>> pendingPermissionExecutions =
            ConcurrentHashMap.newKeySet();
    private final Map<CompletableFuture<CopilotToolExecution>, AtomicBoolean> permissionCancellations =
            new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newScheduledThreadPool(
            1, runnable -> {
                Thread thread = new Thread(runnable, "copilot-tool-timeouts");
                thread.setDaemon(true);
                return thread;
            });

    public void register(CopilotToolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        CopilotToolDefinition existing = definitions.putIfAbsent(definition.name(), definition);
        if (existing != null && !definition.overridesBuiltInTool()) {
            throw new IllegalArgumentException("Duplicate tool name: " + definition.name()
                    + ". Set overridesBuiltInTool=true to replace an existing declaration.");
        }
        if (existing != null) {
            definitions.put(definition.name(), definition);
        }
    }

    /** Register a legacy Java tool through the JSON-schema adapter. */
    public void register(Tool tool) {
        register(CopilotToolAdapter.fromTool(tool));
    }

    public boolean registerIfAbsent(Tool tool) {
        return registerIfAbsent(CopilotToolAdapter.fromTool(tool));
    }

    public boolean registerIfAbsent(CopilotToolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        CopilotToolDefinition existing = definitions.putIfAbsent(definition.name(), definition);
        if (existing == null) {
            return true;
        }
        if (definition.overridesBuiltInTool()) {
            definitions.put(definition.name(), definition);
            return true;
        }
        return false;
    }

    public CopilotToolDefinition unregister(String name) {
        return name == null ? null : definitions.remove(name);
    }

    public Optional<CopilotToolDefinition> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(definitions.get(name));
    }

    public CopilotToolDefinition get(String name) {
        return name == null ? null : definitions.get(name);
    }

    public List<CopilotToolDefinition> all() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(CopilotToolDefinition::name))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<String> names() {
        return all().stream().map(CopilotToolDefinition::name).collect(Collectors.toUnmodifiableList());
    }

    /** Whether a tool participates in the permission policy. */
    public boolean requiresPermission(String name) {
        CopilotToolDefinition definition = get(name);
        return definition != null && !definition.skipPermission();
    }

    /** Current interactive permission prompts, in deterministic request-id order. */
    public List<CopilotPermissionRequest> pendingPermissionRequests() {
        return pendingPermissionRequests.values().stream()
                .sorted(Comparator.comparing(CopilotPermissionRequest::requestId))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<CopilotPermissionRequest> getPendingPermissionRequests() {
        return pendingPermissionRequests();
    }

    /** Resolve a pending permission prompt from an interactive host. */
    public boolean resolvePermission(String requestId, CopilotPermissionDecision decision) {
        CopilotPermissionRequest request = requestId == null ? null : pendingPermissionRequests.get(requestId);
        return request != null && request.resolve(decision);
    }

    public boolean denyPermission(String requestId) {
        return resolvePermission(requestId, CopilotPermissionDecision.DENY);
    }

    /** Invoke a model request and normalize every failure into a model-visible result. */
    public CompletableFuture<CopilotToolExecution> execute(
            String sessionId,
            ToolExecutionRequest request,
            CopilotLoopConfig config) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return CompletableFuture.completedFuture(CopilotToolExecution.failure(
                    request == null ? null : request.id(), "Tool name is missing"));
        }
        CopilotToolDefinition definition = definitions.get(request.name());
        if (definition == null) {
            return CompletableFuture.completedFuture(CopilotToolExecution.failure(
                    request.id(), "Unknown tool: " + request.name()));
        }

        Map<String, Object> arguments;
        try {
            arguments = parseArguments(request.arguments());
            validateSchema(definition.parameters(), arguments, "arguments");
        } catch (Exception error) {
            return CompletableFuture.completedFuture(CopilotToolExecution.failure(
                    request.id(), "Invalid arguments for " + request.name() + ": " + error.getMessage()));
        }

        CopilotToolInvocation invocation = new CopilotToolInvocation(
                sessionId,
                request.id(),
                request.name(),
                arguments,
                invocationContext(config, sessionId, request));
        if (!definition.skipPermission()) {
            CopilotPermissionHandler permissionHandler = config == null
                    ? (value, tool) -> CopilotPermissionDecision.ASK
                    : config.getPermissionHandler();
            try {
                CopilotPermissionDecision decision = permissionHandler == null
                        ? CopilotPermissionDecision.DENY : permissionHandler.check(invocation, definition);
                if (decision == CopilotPermissionDecision.ASK) {
                    // The Copilot protocol leaves requests pending when no
                    // callback is configured. Hosts can inspect the registry
                    // and resolve the request later through resolvePermission.
                    CopilotPermissionRequestHandler requestHandler = config == null
                            || config.getPermissionRequestHandler() == null
                            ? pendingRequest -> pendingRequest.decision()
                            : config.getPermissionRequestHandler();
                    CopilotLoopConfig effectiveConfig = config == null
                            ? new CopilotLoopConfig() : config;
                    return executeAfterPermission(request, definition, invocation,
                            effectiveConfig, requestHandler);
                }
                if (decision != CopilotPermissionDecision.ALLOW) {
                    return CompletableFuture.completedFuture(CopilotToolExecution.denied(
                            request.id(), "Permission denied for tool: " + request.name()));
                }
            } catch (Throwable error) {
                return CompletableFuture.completedFuture(CopilotToolExecution.error(
                        request.id(), "Permission check failed for tool " + request.name() + ": "
                                + rootMessage(error)));
            }
        }

        return invokeAuthorized(request, definition, invocation, config);
    }

    private CompletableFuture<CopilotToolExecution> executeAfterPermission(
            ToolExecutionRequest request,
            CopilotToolDefinition definition,
            CopilotToolInvocation invocation,
            CopilotLoopConfig config,
            CopilotPermissionRequestHandler requestHandler) {
        String permissionId = buildPermissionId(invocation.getSessionId(), request);
        CopilotPermissionRequest permissionRequest = new CopilotPermissionRequest(
                permissionId, invocation, definition);
        pendingPermissionRequests.put(permissionId, permissionRequest);
        if (config.getPermissionRequestSink() != null) {
            try {
                config.getPermissionRequestSink().accept(permissionRequest);
            } catch (Throwable ignored) {
                // An event sink must not prevent the permission future from
                // being resolved by the host.
            }
        }

        CompletionStage<CopilotPermissionDecision> stage;
        try {
            stage = requestHandler.request(permissionRequest);
            if (stage == null) {
                stage = permissionRequest.decision();
            }
        } catch (Throwable error) {
            pendingPermissionRequests.remove(permissionId, permissionRequest);
            return CompletableFuture.completedFuture(CopilotToolExecution.error(
                    request.id(), "Permission request failed: " + rootMessage(error)));
        }

        CompletableFuture<CopilotPermissionDecision> permissionFuture;
        try {
            permissionFuture = stage.toCompletableFuture();
        } catch (Throwable unsupportedStage) {
            CompletableFuture<CopilotPermissionDecision> bridged = new CompletableFuture<>();
            stage.whenComplete((value, error) -> {
                if (error != null) {
                    bridged.completeExceptionally(error);
                } else {
                    bridged.complete(value);
                }
            });
            permissionFuture = bridged;
        }
        CompletableFuture<CopilotToolExecution> result = new CompletableFuture<>();
        activeExecutions.put(result, request.id());
        pendingPermissionExecutions.add(result);
        AtomicBoolean permissionCancelled = new AtomicBoolean(false);
        permissionCancellations.put(result, permissionCancelled);
        CompletableFuture<CopilotPermissionDecision> finalPermissionFuture = permissionFuture;
        permissionFuture.whenComplete((resolved, error) -> {
            pendingPermissionRequests.remove(permissionId, permissionRequest);
            synchronized (permissionCancelled) {
                if (permissionCancelled.get() || result.isDone()) {
                    pendingPermissionExecutions.remove(result);
                    return;
                }
                if (error != null) {
                    result.complete(CopilotToolExecution.error(request.id(),
                            "Permission request failed: " + rootMessage(error)));
                } else if (resolved == CopilotPermissionDecision.ALLOW) {
                    invokeAuthorized(request, definition, invocation, config)
                            .whenComplete((execution, invokeError) -> {
                                if (invokeError != null) {
                                    result.complete(CopilotToolExecution.error(
                                            request.id(), rootMessage(invokeError)));
                                } else {
                                    result.complete(execution);
                                }
                            });
                } else {
                    result.complete(CopilotToolExecution.denied(request.id(),
                            "Permission denied for tool: " + request.name()));
                }
            }
            pendingPermissionExecutions.remove(result);
        });
        result.whenComplete((value, error) -> {
            activeExecutions.remove(result);
            permissionCancellations.remove(result);
            if (result.isCancelled()) {
                finalPermissionFuture.cancel(true);
            }
        });
        return result;
    }

    private CompletableFuture<CopilotToolExecution> invokeAuthorized(
            ToolExecutionRequest request,
            CopilotToolDefinition definition,
            CopilotToolInvocation invocation,
            CopilotLoopConfig config) {
        CompletableFuture<Object> handlerFuture;
        try {
            handlerFuture = CopilotToolAdapter.invokeOnBoundedExecutor(
                    () -> definition.invoke(invocation));
        } catch (Throwable error) {
            return CompletableFuture.completedFuture(CopilotToolExecution.error(
                    request.id(), "Tool handler could not be scheduled: " + rootMessage(error)));
        }
        long timeout = config == null ? 300_000L : config.getToolTimeoutMillis();
        activeHandlers.add(handlerFuture);
        CompletableFuture<CopilotToolExecution> result = new CompletableFuture<>();
        activeExecutions.put(result, request.id());
        result.whenComplete((value, error) -> {
            activeExecutions.remove(result);
            if (result.isCancelled()) {
                handlerFuture.cancel(true);
            }
        });
        ScheduledFuture<?> timeoutTask = TIMEOUT_EXECUTOR.schedule(() -> {
            String message = "Tool timed out after " + timeout + " ms: " + request.name();
            if (result.complete(CopilotToolExecution.timeout(request.id(), message))) {
                handlerFuture.cancel(true);
            }
        }, timeout, TimeUnit.MILLISECONDS);
        handlerFuture.whenComplete((value, error) -> {
            activeHandlers.remove(handlerFuture);
            if (result.isDone()) {
                return;
            }
            timeoutTask.cancel(false);
            if (error != null) {
                result.complete(CopilotToolExecution.error(request.id(), rootMessage(error)));
            } else {
                result.complete(CopilotToolExecution.fromValue(request.id(), value));
            }
        });
        return result;
    }

    private String buildPermissionId(String sessionId, ToolExecutionRequest request) {
        return (sessionId == null ? "session" : sessionId)
                + ":" + (request == null || request.id() == null ? "call" : request.id())
                + ":" + UUID.randomUUID();
    }

    /** Cancel handlers still running when a session is being torn down. */
    public boolean cancelOutstanding(long waitMillis) {
        // Complete the futures consumed by the loop first. A handler may ignore
        // interruption; the session must still be able to leave its join()
        // boundary and persist an explicit aborted result.
        for (Map.Entry<CompletableFuture<CopilotToolExecution>, String> entry
                : new LinkedHashMap<>(activeExecutions).entrySet()) {
            AtomicBoolean permissionCancelled = permissionCancellations.get(entry.getKey());
            if (permissionCancelled != null) {
                synchronized (permissionCancelled) {
                    permissionCancelled.set(true);
                    entry.getKey().complete(CopilotToolExecution.error(
                            entry.getValue(), "Tool execution aborted."));
                }
            } else {
                entry.getKey().complete(CopilotToolExecution.error(
                        entry.getValue(), "Tool execution aborted."));
            }
        }
        for (CopilotPermissionRequest request : pendingPermissionRequests.values()) {
            request.deny();
        }
        for (CompletableFuture<CopilotToolExecution> permission : pendingPermissionExecutions) {
            permission.cancel(true);
        }
        for (CompletableFuture<Object> handler : activeHandlers) {
            handler.cancel(true);
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, waitMillis));
        while (!activeHandlers.isEmpty() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(Math.min(25L, Math.max(1L,
                        TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return activeHandlers.isEmpty();
    }

    /**
     * Cancel handlers and run cleanup only after all physical handler tasks
     * have quiesced.  This prevents a timed-out MCP call from using a staged
     * skill directory after the owning session has deleted it.
     */
    public boolean cancelOutstanding(long waitMillis, Runnable afterQuiescent) {
        boolean complete = cancelOutstanding(waitMillis);
        if (complete) {
            if (afterQuiescent != null) {
                afterQuiescent.run();
            }
            return true;
        }
        if (afterQuiescent != null) {
            Thread waiter = new Thread(() -> {
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
                while (!activeHandlers.isEmpty() && System.nanoTime() < deadline) {
                    for (CompletableFuture<Object> handler : activeHandlers) {
                        handler.cancel(true);
                    }
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (activeHandlers.isEmpty()) {
                    afterQuiescent.run();
                }
            }, "copilot-tool-cleanup");
            waiter.setDaemon(true);
            waiter.start();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JSON.parse(raw);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("arguments must be a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        ((Map<?, ?>) parsed).forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void validateSchema(Map<String, Object> schema, Object value, String path) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Object anyOf = schema.get("anyOf");
        if (anyOf instanceof List<?> && !((List<?>) anyOf).isEmpty()) {
            for (Object option : (List<?>) anyOf) {
                if (option instanceof Map<?, ?>) {
                    try {
                        validateSchema((Map<String, Object>) option, value, path);
                        return;
                    } catch (IllegalArgumentException ignored) {
                        // Try the next branch.
                    }
                }
            }
            throw new IllegalArgumentException(path + " does not match any allowed schema");
        }

        // JSON Schema constraints apply to every type, including objects and
        // arrays.  Validate them before the type-specific early returns below.
        if (schema.containsKey("const") && !Objects.equals(schema.get("const"), value)) {
            throw new IllegalArgumentException(path + " must equal the schema const value");
        }
        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> && !((List<?>) enumValues).contains(value)) {
            throw new IllegalArgumentException(path + " is not an allowed value");
        }
        validateCommonConstraints(schema, value, path);

        Object typeValue = schema.get("type");
        if (typeValue instanceof List<?> && !((List<?>) typeValue).isEmpty()) {
            IllegalArgumentException lastError = null;
            for (Object candidate : (List<?>) typeValue) {
                Map<String, Object> branch = new LinkedHashMap<>(schema);
                branch.put("type", String.valueOf(candidate));
                try {
                    validateSchema(branch, value, path);
                    return;
                } catch (IllegalArgumentException error) {
                    lastError = error;
                }
            }
            throw new IllegalArgumentException(path + " does not match any allowed type", lastError);
        }
        String type = typeValue == null ? null : String.valueOf(typeValue);
        if (type == null || type.isBlank()) {
            return;
        }
        switch (type) {
            case "object":
                if (!(value instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException(path + " must be an object");
                }
                Map<String, Object> object = new LinkedHashMap<>();
                ((Map<?, ?>) value).forEach((key, child) -> object.put(String.valueOf(key), child));
                Object required = schema.get("required");
                if (required instanceof List<?>) {
                    for (Object name : (List<?>) required) {
                        String key = String.valueOf(name);
                        // `required` means the property must be present.  A
                        // present JSON null is valid unless its own schema
                        // disallows null.
                        if (!object.containsKey(key)) {
                            throw new IllegalArgumentException("missing required property '" + key + "'");
                        }
                    }
                }
                Object properties = schema.get("properties");
                Map<?, ?> propertyMap = properties instanceof Map<?, ?> ? (Map<?, ?>) properties : Map.of();
                if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                    for (String key : object.keySet()) {
                        if (!propertyMap.containsKey(key)) {
                            throw new IllegalArgumentException(path + " contains unknown property '" + key + "'");
                        }
                    }
                }
                for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (object.containsKey(key) && entry.getValue() instanceof Map<?, ?>) {
                        validateSchema((Map<String, Object>) entry.getValue(), object.get(key), path + "." + key);
                    }
                }
                return;
            case "array":
                if (!(value instanceof List<?>)) {
                    throw new IllegalArgumentException(path + " must be an array");
                }
                Object items = schema.get("items");
                if (items instanceof Map<?, ?>) {
                    int index = 0;
                    for (Object child : (List<?>) value) {
                        validateSchema((Map<String, Object>) items, child, path + "[" + index++ + "]");
                    }
                }
                return;
            case "string":
                if (!(value instanceof String)) throw new IllegalArgumentException(path + " must be a string");
                break;
            case "boolean":
                if (!(value instanceof Boolean)) throw new IllegalArgumentException(path + " must be a boolean");
                break;
            case "integer":
                if (!(value instanceof Number) || ((Number) value).doubleValue() % 1 != 0) {
                    throw new IllegalArgumentException(path + " must be an integer");
                }
                break;
            case "number":
                if (!(value instanceof Number)) throw new IllegalArgumentException(path + " must be a number");
                break;
            case "null":
                if (value != null) throw new IllegalArgumentException(path + " must be null");
                break;
            default:
                return;
        }

    }

    private void validateCommonConstraints(Map<String, Object> schema, Object value, String path) {
        if (value instanceof String) {
            String text = (String) value;
            checkIntegerBound(schema.get("minLength"), text.length(), path + " length", "at least");
            checkIntegerBound(schema.get("maxLength"), text.length(), path + " length", "at most");
            Object pattern = schema.get("pattern");
            if (pattern != null && !Pattern.compile(String.valueOf(pattern)).matcher(text).find()) {
                throw new IllegalArgumentException(path + " does not match the required pattern");
            }
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            checkIntegerBound(schema.get("minItems"), list.size(), path + " item count", "at least");
            checkIntegerBound(schema.get("maxItems"), list.size(), path + " item count", "at most");
            if (Boolean.TRUE.equals(schema.get("uniqueItems"))
                    && new HashSet<>(list).size() != list.size()) {
                throw new IllegalArgumentException(path + " must contain unique items");
            }
        }
        if (value instanceof Map<?, ?>) {
            int size = ((Map<?, ?>) value).size();
            checkIntegerBound(schema.get("minProperties"), size, path + " property count", "at least");
            checkIntegerBound(schema.get("maxProperties"), size, path + " property count", "at most");
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            checkNumericBound(schema.get("minimum"), number, true, false, path);
            checkNumericBound(schema.get("exclusiveMinimum"), number, true, true, path);
            checkNumericBound(schema.get("maximum"), number, false, false, path);
            checkNumericBound(schema.get("exclusiveMaximum"), number, false, true, path);
        }
    }

    private void checkIntegerBound(Object configured,
                                   int actual,
                                   String path,
                                   String relation) {
        if (configured instanceof Number) {
            int expected = ((Number) configured).intValue();
            boolean valid = "at least".equals(relation) ? actual >= expected : actual <= expected;
            if (!valid) {
                throw new IllegalArgumentException(path + " must be " + relation + " " + expected);
            }
        }
    }

    private void checkNumericBound(Object configured,
                                   double actual,
                                   boolean minimum,
                                   boolean exclusive,
                                   String path) {
        if (!(configured instanceof Number)) {
            return;
        }
        double expected = ((Number) configured).doubleValue();
        boolean valid;
        if (minimum) {
            valid = exclusive ? actual > expected : actual >= expected;
        } else {
            valid = exclusive ? actual < expected : actual <= expected;
        }
        if (!valid) {
            String relation = minimum
                    ? (exclusive ? "greater than " : "at least ")
                    : (exclusive ? "less than " : "at most ");
            throw new IllegalArgumentException(path + " must be " + relation + expected);
        }
    }

    private Map<String, Object> invocationContext(CopilotLoopConfig config,
                                                   String sessionId,
                                                   ToolExecutionRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (config != null && config.getInvocationContext() != null) {
            context.putAll(config.getInvocationContext());
        }
        // These canonical fields are always available to handlers, while
        // explicit host metadata remains intact under its own keys.
        context.putIfAbsent("sessionId", sessionId);
        context.putIfAbsent("toolCallId", request == null ? null : request.id());
        context.putIfAbsent("toolName", request == null ? null : request.name());
        return context;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public static final class CopilotToolExecution {
        private final String toolCallId;
        private final boolean success;
        private final Object result;
        private final String error;
        private final String modelText;
        private final String resultType;

        private CopilotToolExecution(String toolCallId,
                                     boolean success,
                                     Object result,
                                     String error,
                                     String modelText,
                                     String resultType) {
            this.toolCallId = toolCallId;
            this.success = success;
            this.result = result;
            this.error = error;
            this.modelText = modelText;
            this.resultType = resultType;
        }

        public static CopilotToolExecution success(String id, Object result) {
            return fromValue(id, result);
        }

        public static CopilotToolExecution error(String id, String error) {
            return new CopilotToolExecution(id, false, null, error,
                    "Tool error: " + error, CopilotToolResult.ResultType.ERROR.wireValue());
        }

        public static CopilotToolExecution failure(String id, String error) {
            return new CopilotToolExecution(id, false, null, error,
                    "Tool failure: " + error, CopilotToolResult.ResultType.FAILURE.wireValue());
        }

        public static CopilotToolExecution timeout(String id, String message) {
            return new CopilotToolExecution(id, false, null, message, message,
                    CopilotToolResult.ResultType.TIMEOUT.wireValue());
        }

        public static CopilotToolExecution denied(String id, String message) {
            return new CopilotToolExecution(id, false, null, message, message,
                    CopilotToolResult.ResultType.DENIED.wireValue());
        }

        public static CopilotToolExecution rejected(String id, String message) {
            return new CopilotToolExecution(id, false, null, message, message,
                    CopilotToolResult.ResultType.REJECTED.wireValue());
        }

        private static CopilotToolExecution fromValue(String id, Object value) {
            if (value instanceof CopilotToolResult) {
                CopilotToolResult structured = (CopilotToolResult) value;
                boolean success = structured.isSuccess();
                String modelText = structured.textResultForLlm();
                if ((modelText == null || modelText.isBlank())
                        && !structured.binaryResultsForLlm().isEmpty()) {
                    // LangChain4j's text-only ToolExecutionResultMessage cannot
                    // carry binary parts. Preserve the structured wire result
                    // for the next model turn instead of silently dropping it.
                    modelText = structured.toString();
                }
                if (!success && (modelText == null || modelText.isBlank())) {
                    modelText = "Tool error: " + structured.error();
                }
                return new CopilotToolExecution(id, success, value, structured.error(),
                        modelText, structured.resultType());
            }
            return new CopilotToolExecution(id, true, value, null, stringify(value),
                    CopilotToolResult.ResultType.SUCCESS.wireValue());
        }

        private static String stringify(Object value) {
            if (value == null) return "null";
            if (value instanceof String) return (String) value;
            return JSON.toJSONString(value);
        }

        public String getToolCallId() { return toolCallId; }
        public String toolCallId() { return toolCallId; }
        public boolean isSuccess() { return success; }
        public boolean success() { return success; }
        public Object getResult() { return result; }
        public Object result() { return result; }
        public String getError() { return error; }
        public String error() { return error; }
        public String getModelText() { return modelText; }
        public String getResultType() { return resultType; }
        public String resultType() { return resultType; }

        public String modelText() {
            return modelText;
        }
    }
}
