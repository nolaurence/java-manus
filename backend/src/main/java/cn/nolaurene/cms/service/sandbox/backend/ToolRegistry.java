package cn.nolaurene.cms.service.sandbox.backend;


import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;

import java.util.*;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }
}
