package cn.nolaurene.cms.service.sandbox.backend.tool;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public interface Tool {

    String name();

    String description();

    String run(String input, Map<String, Object> context);

    /** JSON Schema exposed to function-calling runtimes such as Copilot. */
    default Map<String, Object> parametersSchema() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "string");
        input.put("description", "Input passed to the tool");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("input", input);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("input"));
        return schema;
    }

    /** Whether the tool is safe to run without a permission prompt. */
    default boolean skipPermission() {
        return false;
    }

    /**
     * Structured, asynchronous invocation bridge used by the Copilot-compatible
     * Java runtime.
     * Existing tools keep their string-based {@link #run(String, Map)} method.
     */
    default CompletableFuture<Object> invoke(Map<String, Object> arguments,
                                             Map<String, Object> context) {
        Map<String, Object> safeArguments = arguments == null
                ? java.util.Collections.emptyMap()
                : arguments;
        Object input = safeArguments.get("input");
        String serialized = input == null ? "" : String.valueOf(input);
        // The Copilot adapter owns the bounded execution pool and cancellation
        // propagation.  Keep this bridge synchronous so a legacy tool does not
        // escape into the global common pool.
        return CompletableFuture.completedFuture(run(serialized, context));
    }
}
