package cn.nolaurene.cms.service.sandbox.backend;


import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotToolAdapter;
import cn.nolaurene.cms.service.sandbox.backend.copilot.CopilotToolDefinition;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public void register(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        if (tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        tools.put(tool.name(), tool);
    }

    /** Register a tool only when its name is not already occupied. */
    public boolean registerIfAbsent(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        if (tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        return tools.putIfAbsent(tool.name(), tool) == null;
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> all() {
        return tools.values().stream()
                .sorted(Comparator.comparing(Tool::name))
                .collect(Collectors.toList());
    }

    public List<String> getToolNames() {
        return tools.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * Convert the registry to Copilot's standard JSON-schema tool definitions.
     * The returned list is deterministic, which keeps session creation and
     * tests reproducible.
     */
    public List<CopilotToolDefinition> toCopilotToolDefinitions() {
        return all().stream()
                .map(CopilotToolAdapter::fromTool)
                .collect(Collectors.toList());
    }
}
