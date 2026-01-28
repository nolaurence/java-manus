package cn.nolaurene.cms.service.sandbox.backend.agent;


import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.Tool;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author nolaurence
 * @date 2026/1/28 下午1:36
 * @description:
 */
@Data
public class AgentContext {

    private String currentBrowserSnapshot;
    private String currentShellView;
    private String currentFile;

    private List<String> toolList = null;

    public boolean hitBrowser() {
        Set<String> hitSet = toolList.parallelStream().map(tool -> {
            String[] s = tool.split("_");
            if (s.length == 0) {
                return "";
            }
            return s[0];
        }).collect(Collectors.toSet());
        return hitSet.contains("browser");
    }

    public boolean hitShell() {
        Set<String> hitSet = toolList.parallelStream().map(tool -> {
            String[] s = tool.split("_");
            if (s.length == 0) {
                return "";
            }
            return s[0];
        }).collect(Collectors.toSet());
        return hitSet.contains("shell");
    }

    public void addTool(String toolName) {
        if (null == toolList) {
            toolList = new ArrayList<>();
        }
        toolList.add(toolName);
    }
}
