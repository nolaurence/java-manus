package cn.nolaurene.cms.service.sandbox.backend.agent;


import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author nolaurence
 * @date 2026/1/28 下午1:36
 * @description: Agent execution context that maintains real-time state information
 */
@Data
public class AgentContext {

    /**
     * Current browser page snapshot (accessibility tree / aria snapshot)
     */
    private String currentBrowserSnapshot;
    
    /**
     * Last browser URL visited
     */
    private String currentBrowserUrl;
    
    /**
     * Current shell/terminal output view
     */
    private String currentShellView;
    
    /**
     * Current working directory in shell
     */
    private String currentWorkingDirectory;
    
    /**
     * Current file path being edited/viewed
     */
    private String currentFile;
    
    /**
     * Current file content (truncated if too long)
     */
    private String currentFileContent;
    
    /**
     * Last tool execution result
     */
    private String lastToolResult;
    
    /**
     * Last executed tool name
     */
    private String lastToolName;

    private List<String> toolList = null;
    
    private static final int MAX_SNAPSHOT_LENGTH = 8000;
    private static final int MAX_SHELL_OUTPUT_LENGTH = 4000;
    private static final int MAX_FILE_CONTENT_LENGTH = 4000;

    public boolean hitBrowser() {
        if (toolList == null || toolList.isEmpty()) {
            return false;
        }
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
        if (toolList == null || toolList.isEmpty()) {
            return false;
        }
        Set<String> hitSet = toolList.parallelStream().map(tool -> {
            String[] s = tool.split("_");
            if (s.length == 0) {
                return "";
            }
            return s[0];
        }).collect(Collectors.toSet());
        return hitSet.contains("shell");
    }
    
    public boolean hitFile() {
        if (toolList == null || toolList.isEmpty()) {
            return false;
        }
        Set<String> hitSet = toolList.parallelStream().map(tool -> {
            String[] s = tool.split("_");
            if (s.length == 0) {
                return "";
            }
            return s[0];
        }).collect(Collectors.toSet());
        return hitSet.contains("file");
    }

    public void addTool(String toolName) {
        if (null == toolList) {
            toolList = new ArrayList<>();
        }
        toolList.add(toolName);
    }
    
    /**
     * Update browser context from tool execution result
     * @param toolName the browser tool name
     * @param result the tool execution result
     */
    public void updateBrowserContext(String toolName, String result) {
        this.lastToolName = toolName;
        this.lastToolResult = truncate(result, MAX_SNAPSHOT_LENGTH);
        
        // browser_snapshot returns the page accessibility tree
        if ("browser_snapshot".equals(toolName)) {
            this.currentBrowserSnapshot = truncate(result, MAX_SNAPSHOT_LENGTH);
        }
        // browser_navigate updates the URL
        else if ("browser_navigate".equals(toolName)) {
            // Try to extract URL from result if available
            this.currentBrowserSnapshot = truncate(result, MAX_SNAPSHOT_LENGTH);
        }
        // Other browser operations may also update the snapshot
        else if (toolName.startsWith("browser_")) {
            // Most browser operations return updated page state
            this.currentBrowserSnapshot = truncate(result, MAX_SNAPSHOT_LENGTH);
        }
    }
    
    /**
     * Update shell context from tool execution result
     * @param toolName the shell tool name
     * @param result the tool execution result
     */
    public void updateShellContext(String toolName, String result) {
        this.lastToolName = toolName;
        this.lastToolResult = truncate(result, MAX_SHELL_OUTPUT_LENGTH);
        
        if (toolName.startsWith("shell_")) {
            this.currentShellView = truncate(result, MAX_SHELL_OUTPUT_LENGTH);
        }
    }
    
    /**
     * Update file context from tool execution result
     * @param toolName the file tool name
     * @param result the tool execution result
     * @param filePath the file path if available
     */
    public void updateFileContext(String toolName, String result, String filePath) {
        this.lastToolName = toolName;
        this.lastToolResult = truncate(result, MAX_FILE_CONTENT_LENGTH);
        
        if (StringUtils.isNotBlank(filePath)) {
            this.currentFile = filePath;
        }
        
        if ("file_read".equals(toolName)) {
            this.currentFileContent = truncate(result, MAX_FILE_CONTENT_LENGTH);
        }
    }
    
    /**
     * Render the context as a formatted string for LLM prompt
     * @return formatted context string
     */
    public String renderForPrompt() {
        StringBuilder sb = new StringBuilder();
        
        // Browser context section
        if (hitBrowser() && StringUtils.isNotBlank(currentBrowserSnapshot)) {
            sb.append("\n## Current Browser State\n");
            if (StringUtils.isNotBlank(currentBrowserUrl)) {
                sb.append("- URL: ").append(currentBrowserUrl).append("\n");
            }
            sb.append("- Page Snapshot (Accessibility Tree):\n```\n");
            sb.append(currentBrowserSnapshot);
            sb.append("\n```\n");
        }
        
        // Shell context section
        if (hitShell() && StringUtils.isNotBlank(currentShellView)) {
            sb.append("\n## Current Shell State\n");
            if (StringUtils.isNotBlank(currentWorkingDirectory)) {
                sb.append("- Working Directory: ").append(currentWorkingDirectory).append("\n");
            }
            sb.append("- Recent Output:\n```\n");
            sb.append(currentShellView);
            sb.append("\n```\n");
        }
        
        // File context section
        if (hitFile() && StringUtils.isNotBlank(currentFile)) {
            sb.append("\n## Current File Context\n");
            sb.append("- File: ").append(currentFile).append("\n");
            if (StringUtils.isNotBlank(currentFileContent)) {
                sb.append("- Content:\n```\n");
                sb.append(currentFileContent);
                sb.append("\n```\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Truncate string to max length with ellipsis
     */
    private String truncate(String str, int maxLength) {
        if (StringUtils.isBlank(str)) {
            return str;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 20) + "\n... [truncated, " + (str.length() - maxLength + 20) + " chars omitted]";
    }
    
    /**
     * Clear all context (useful for reset between sessions)
     */
    public void clearContext() {
        this.currentBrowserSnapshot = null;
        this.currentBrowserUrl = null;
        this.currentShellView = null;
        this.currentWorkingDirectory = null;
        this.currentFile = null;
        this.currentFileContent = null;
        this.lastToolResult = null;
        this.lastToolName = null;
    }
}
