package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JSON-schema based tool definition used by the in-process Java agent runtime.
 * It intentionally mirrors the stable parts of Copilot's tool contract without
 * depending on the Copilot SDK or CLI.
 */
public final class CopilotToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;
    private final CopilotToolHandler handler;
    private final boolean skipPermission;
    private final boolean overridesBuiltInTool;
    private final boolean terminal;
    private final String defer;
    private final Map<String, Object> metadata;

    public CopilotToolDefinition(String name,
                                 String description,
                                 Map<String, Object> parameters,
                                 CopilotToolHandler handler) {
        this(name, description, parameters, handler, false, false, false, Collections.emptyMap());
    }

    public CopilotToolDefinition(String name,
                                 String description,
                                 Map<String, Object> parameters,
                                 CopilotToolHandler handler,
                                 boolean skipPermission,
                                 boolean overridesBuiltInTool,
                                 boolean terminal,
                                 Map<String, Object> metadata) {
        this(name, description, parameters, handler, skipPermission,
                overridesBuiltInTool, terminal, null, metadata);
    }

    /**
     * Compatibility constructor matching the stable Copilot Java tool shape.
     * The extra terminal flag remains available through the richer overload.
     */
    public CopilotToolDefinition(String name,
                                 String description,
                                 Map<String, Object> parameters,
                                 CopilotToolHandler handler,
                                 Boolean overridesBuiltInTool,
                                 Boolean skipPermission,
                                 CopilotToolDefer defer) {
        this(name, description, parameters, handler,
                Boolean.TRUE.equals(skipPermission),
                Boolean.TRUE.equals(overridesBuiltInTool),
                false,
                defer == null ? null : defer.wireValue(),
                Collections.emptyMap());
    }

    /** Canonical compatibility constructor with host-defined metadata. */
    public CopilotToolDefinition(String name,
                                 String description,
                                 Map<String, Object> parameters,
                                 CopilotToolHandler handler,
                                 Boolean overridesBuiltInTool,
                                 Boolean skipPermission,
                                 CopilotToolDefer defer,
                                 Map<String, Object> metadata) {
        this(name, description, parameters, handler,
                Boolean.TRUE.equals(skipPermission),
                Boolean.TRUE.equals(overridesBuiltInTool),
                false,
                defer == null ? null : defer.wireValue(),
                metadata);
    }

    /**
     * Full definition constructor.  {@code defer} follows the Copilot tool
     * contract and accepts {@code auto}, {@code never}, or {@code null}.
     */
    public CopilotToolDefinition(String name,
                                 String description,
                                 Map<String, Object> parameters,
                                 CopilotToolHandler handler,
                                 boolean skipPermission,
                                 boolean overridesBuiltInTool,
                                 boolean terminal,
                                 String defer,
                                 Map<String, Object> metadata) {
        this.name = validateName(name);
        this.description = description == null ? "" : description;
        this.parameters = immutableSchema(parameters);
        // A null handler is a valid declaration-only tool.  Hosts may execute
        // such a tool externally and feed the result back into the loop.
        this.handler = handler;
        this.skipPermission = skipPermission;
        this.overridesBuiltInTool = overridesBuiltInTool;
        this.terminal = terminal;
        this.defer = validateDefer(defer);
        this.metadata = metadata == null || metadata.isEmpty()
                ? Collections.emptyMap()
                : freezeMap(metadata);
    }

    public static CopilotToolDefinition create(String name,
                                               String description,
                                               Map<String, Object> parameters,
                                               CopilotToolHandler handler) {
        return new CopilotToolDefinition(name, description, parameters, handler);
    }

    public static CopilotToolDefinition createOverride(String name,
                                                       String description,
                                                       Map<String, Object> parameters,
                                                       CopilotToolHandler handler) {
        return new CopilotToolDefinition(name, description, parameters, handler,
                false, true, false, null, Collections.emptyMap());
    }

    public static CopilotToolDefinition createSkipPermission(String name,
                                                             String description,
                                                             Map<String, Object> parameters,
                                                             CopilotToolHandler handler) {
        return new CopilotToolDefinition(name, description, parameters, handler,
                true, false, false, null, Collections.emptyMap());
    }

    public static CopilotToolDefinition createWithDefer(String name,
                                                        String description,
                                                        Map<String, Object> parameters,
                                                        CopilotToolHandler handler,
                                                        CopilotToolDefer defer) {
        return new CopilotToolDefinition(name, description, parameters, handler,
                false, false, false, defer == null ? null : defer.wireValue(),
                Collections.emptyMap());
    }

    public static CopilotToolDefinition createWithMetadata(String name,
                                                           String description,
                                                           Map<String, Object> parameters,
                                                           CopilotToolHandler handler,
                                                           Map<String, Object> metadata) {
        return new CopilotToolDefinition(name, description, parameters, handler,
                false, false, false, null, metadata);
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String description() {
        return description;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /** Alias used by JSON-schema oriented integrations. */
    public Map<String, Object> parametersSchema() {
        return parameters;
    }

    /** A wire-shaped definition useful for persistence or API responses. */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("description", description);
        result.put("parameters", parameters);
        if (skipPermission) result.put("skipPermission", true);
        if (overridesBuiltInTool) result.put("overridesBuiltInTool", true);
        if (terminal) result.put("isTerminal", true);
        if (defer != null) result.put("defer", defer);
        if (!metadata.isEmpty()) result.put("metadata", metadata);
        return Collections.unmodifiableMap(result);
    }

    public CopilotToolHandler handler() {
        return handler;
    }

    public boolean skipPermission() {
        return skipPermission;
    }

    public boolean isSkipPermission() {
        return skipPermission;
    }

    public boolean overridesBuiltInTool() {
        return overridesBuiltInTool;
    }

    public boolean isOverridesBuiltInTool() {
        return overridesBuiltInTool;
    }

    /** Immutable option modifier matching the Copilot Java tool API. */
    public CopilotToolDefinition overridesBuiltInTool(boolean value) {
        return copy(value, skipPermission, terminal, defer, metadata);
    }

    public boolean isTerminal() {
        return terminal;
    }

    public String defer() {
        return defer;
    }

    public String getDefer() {
        return defer;
    }

    public CopilotToolDefer deferMode() {
        return CopilotToolDefer.fromWire(defer);
    }

    public CopilotToolDefinition defer(CopilotToolDefer value) {
        return copy(overridesBuiltInTool, skipPermission, terminal,
                value == null ? null : value.wireValue(), metadata);
    }

    public CopilotToolDefinition skipPermission(boolean value) {
        return copy(overridesBuiltInTool, value, terminal, defer, metadata);
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public CopilotToolDefinition metadata(Map<String, Object> value) {
        return new CopilotToolDefinition(name, description, parameters, handler,
                skipPermission, overridesBuiltInTool, terminal, defer, value);
    }

    public CompletableFuture<Object> invoke(CopilotToolInvocation invocation) {
        if (handler == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Tool has no local handler: " + name));
        }
        try {
            CompletableFuture<Object> result = handler.handle(invocation);
            return result == null
                    ? CompletableFuture.completedFuture(null)
                    : result;
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private CopilotToolDefinition copy(boolean override,
                                       boolean skip,
                                       boolean terminalValue,
                                       String deferValue,
                                       Map<String, Object> metadataValue) {
        return new CopilotToolDefinition(name, description, parameters, handler,
                skip, override, terminalValue, deferValue, metadataValue);
    }

    private static String validateName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("invalid tool name: " + value);
        }
        return value;
    }

    private static String validateDefer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!"auto".equals(value) && !"never".equals(value)) {
            throw new IllegalArgumentException("defer must be 'auto' or 'never'");
        }
        return value;
    }

    private static Map<String, Object> immutableSchema(Map<String, Object> schema) {
        Map<String, Object> result = schema == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(schema);
        result.putIfAbsent("type", "object");
        result.putIfAbsent("properties", new LinkedHashMap<String, Object>());
        return freezeMap(result);
    }

    private static Map<String, Object> freezeMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, freeze(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> nested = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, child) ->
                    nested.put(String.valueOf(key), freeze(child)));
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof List<?>) {
            List<Object> nested = new ArrayList<>();
            for (Object child : (List<?>) value) {
                nested.add(freeze(child));
            }
            return Collections.unmodifiableList(nested);
        }
        return value;
    }
}
