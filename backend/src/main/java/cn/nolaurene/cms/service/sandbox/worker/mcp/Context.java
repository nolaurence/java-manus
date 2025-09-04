package cn.nolaurene.cms.service.sandbox.worker.mcp;

import cn.nolaurene.cms.service.sandbox.worker.mcp.server.DialogModalState;
import cn.nolaurene.cms.service.sandbox.worker.mcp.context.*;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalState;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.Tool;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.ToolActionResult;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.ToolResult;
import com.alibaba.fastjson.JSON;
import com.microsoft.playwright.*;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 操作浏览器的主类
 */
@Slf4j
@Getter
public class Context {

    private final List<Tool> tools;
    private final FullConfig config;
    private final BrowserContext browserContext;
    private final List<Tab> tabs = new ArrayList<>();
    private Tab currentTab;
    private final List<ModalStateWithTab> modalStates = new ArrayList<>();
    private PendingAction pendingAction;
    private final List<DownloadEntry> downloads = new ArrayList<>();
    private ClientVersion clientVersion;

    public Context(List<Tool> tools, FullConfig config, BrowserContext browserContext) {
        this.tools = Objects.requireNonNull(tools, "tools cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.browserContext = Objects.requireNonNull(browserContext, "browserContext cannot be null");

        initializeExistingPages();
        setupPageListener();

        log.debug("ContextJava11 created with {} tools", tools.size());
    }

    // 使用 Java 11 的字符串方法和 Optional
    public boolean clientSupportsImages() {
        switch (config.getImageResponses()) {
            case "allow":
                return true;
            case "omit":
                return false;
            default:
                if (null == clientVersion) {
                    return true;
                }
                if (StringUtils.isBlank(clientVersion.getName())) {
                    return true;
                }
                if (!clientVersion.getName().contains("cursor")) {
                    return true;
                }
                return false;
        }
    }

    public List<ModalStateWithTab> modalStates() {
        return modalStates;
    }

    public void setModalState(ModalState modalState, Tab inTab) {
        modalStates.add(new ModalStateWithTab(modalState, inTab));
    }

    public void clearModalState(ModalState modalState) {
        modalStates.removeIf(state -> state.equals(modalState));
    }

    // 使用 Java 11 的字符串方法和 Optional 优化
    public List<String> modalStatesMarkdown() {
        List<String> result = new ArrayList<String>();
        result.add("### Modal state");

        if (modalStates.isEmpty()) {
            result.add("- There is no modal state present");
//            return result;
        }

        for (ModalStateWithTab state : modalStates) {
            List<Tool> toolFindResult = tools.stream()
                    .filter(toolItem -> toolItem.getClearsModalState().equals(state.getType()))
                    .collect(Collectors.toList());
            if (!toolFindResult.isEmpty()) {
                result.add(String.format("- [%s]: can be handled by the \"${%s}\" tool", state.getDescription(), toolFindResult.get(0).getSchema().getName()));
            }
        }
        return result;
    }

    public List<Tab> tabs() {
        return List.copyOf(tabs);
    }

    public Tab currentTabOrDie() {
        if (null == currentTab) {
            throw new RuntimeException("No current snapshot available. Capture a snapshot or navigate to a new location first.");
        }
        return currentTab;
    }

    public Tab newTab() {
        Page page = browserContext.newPage();
        currentTab = tabs.stream()
                .filter(t -> t.getPage() == page)
                .findFirst()
                .orElse(null);
        return currentTab;
    }

    public void selectTab(int index) {
        if (index < 1 || index > tabs.size()) {
            throw new IllegalArgumentException(String.format("Invalid tab index: %d. Valid range: 1-%d", index, tabs.size()));
        }

        currentTab = tabs.get(index - 1);
        currentTab.getPage().bringToFront();
    }

    public Tab ensureTab() {
        if (null == currentTab) {
            newTab();
        }
        return currentTab;
    }

    // 使用 Java 11 的字符串方法优化
    public String listTabsMarkdown() {
        if (tabs.isEmpty()) {
            return "### No tabs open";
        }

        List<String> lines = new ArrayList<>();
        lines.add("### Open tabs");

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            String title = tab.getPage().title();
            String url = tab.getPage().url();
            String current = tab == currentTab ? " (current)" : "";

            lines.add(String.format("- %d:%s [%s] (%s)", i + 1, current, title, url));
        }

        return String.join("\n", lines);
    }

    public String closeTab(Integer index) {
        Tab tab = null == index ? currentTab : tabs.get(index - 1);
        tab.getPage().close();
        return listTabsMarkdown();
    }

    public ToolActionResult run(Tool tool, Map<String, Object> params, Class clazz) {
        try {
            ToolResult toolResult;
            if (clazz == Object.class && MapUtils.isEmpty(params)) {
                toolResult = tool.getHandler().execute(this, null);
            } else {
                Object toolInput = JSON.parseObject(JSON.toJSONString(params), clazz);
                toolResult = tool.getHandler().execute(this, toolInput);
            }

            CompletableFuture<ToolActionResult> racingAction = toolResult.getAction() != null ? raceAgainstModalDialogs(toolResult.getAction()) : null;

            if (null != toolResult.getResultOverride()) {
                return toolResult.getResultOverride();
            }

            if (null == currentTab) {
                McpSchema.TextContent textContent = new McpSchema.TextContent();
                textContent.setText("No open pages available. Use the \"browser_navigate\" tool to navigate to a page first.");
                return new ToolActionResult(textContent);
            }

            Tab tab = currentTabOrDie();
            ToolActionResult actionResult;

            try {
                if (toolResult.isWaitForNetwork()) {
                    actionResult = Utils.waitForCompletion(this, tab, toolResult.getAction()).get();
                } else {
                    actionResult = null == racingAction ? null : racingAction.get();
                }
            } finally {
                if (toolResult.hasCaptureSnapshot()) {
                    tab.captureSnapshot();
                }
            }

            List<String> result = new ArrayList<>();
            result.add(String.format("- Ran Playwright code:\n```js\n%s\n```", String.join("\n", toolResult.getCode())));

            if (CollectionUtils.isNotEmpty(modalStates())) {
                result.addAll(modalStatesMarkdown());

                McpSchema.TextContent textContent = new McpSchema.TextContent();
                textContent.setText(String.join("\n", result));
                return new ToolActionResult(textContent);
            }

            if (CollectionUtils.isNotEmpty(downloads)) {
                result.add("");
                result.add("### Downloads");
                for (DownloadEntry entry : downloads) {
                    if (entry.isFinished()) {
                        result.add(String.format("- Downloaded file %s to %s", entry.getDownload().suggestedFilename(), entry.getOutputFile()));
                    } else {
                        result.add(String.format("- Downloading file %s ...", entry.getDownload().suggestedFilename()));
                    }
                }
                result.add("");
            }

            if (CollectionUtils.isNotEmpty(tabs)) {
                result.add(listTabsMarkdown());
                result.add("");
                result.add("### Current tab");
            }
            result.addAll(List.of(
                    String.format("- Page URL: %s", tab.getPage().url()),
                    String.format("- Page Title: %s", tab.getPage().url())));
            if (toolResult.hasCaptureSnapshot() && tab.hasSnapshot()) {
                result.add(tab.snapshotOrDie().text());
            }
            List<McpSchema.Content> content = (null != actionResult && CollectionUtils.isNotEmpty(actionResult.getContent())) ? actionResult.getContent() : new ArrayList<>();
            content.add(new McpSchema.TextContent(String.join("\n", result)));
            log.info("[RETURN] {}", JSON.toJSONString(content));
            return new ToolActionResult(content);
        } catch (Exception e) {
            log.error("Error running tool: {}", tool.getSchema().getName(), e);
            return new ToolActionResult(new McpSchema.TextContent("Error executing tool: " + e.getMessage()));
        }
    }

    /**
     * 等待
     * @param timeout ms
     */
    public void waitForTimeout(int timeout) throws InterruptedException {
        if (null == currentTab || isJavaScriptBlocked()) {
            Thread.sleep(timeout);
        }
        // 执行js代码实现wait
        currentTab.getPage().evaluate("() => new Promise(f => setTimeout(f, 1000))");
    }

    private CompletableFuture<ToolActionResult> raceAgainstModalDialogs(
            Supplier<CompletableFuture<ToolActionResult>> action) throws Exception {
        this.pendingAction = new PendingAction();
        pendingAction.setDialogShown(new CompletableFuture<>());

        CompletableFuture<ToolActionResult> resultFuture = new CompletableFuture<>();

        try {
            CompletableFuture<ToolActionResult> actionFuture = action.get();

            CompletableFuture<Void> dialogFuture = pendingAction.getDialogShown();
            CompletableFuture.anyOf(actionFuture, dialogFuture)
                    .whenComplete((outcome, throwable) -> {
                        if (null != throwable) {
                            resultFuture.completeExceptionally(throwable);
                        } else if (outcome instanceof ToolActionResult) {
                            resultFuture.complete((ToolActionResult) outcome);
                        } else {
                            resultFuture.complete(null);
                        }
                    });
        } catch (Exception e) {
            resultFuture.completeExceptionally(e);
        } finally {
            this.pendingAction = null;
        }
        return resultFuture;
    }

    private boolean isJavaScriptBlocked() {
        return modalStates.stream()
                .anyMatch(state -> "dialog".equals(state.getType()));
    }

    public void dialogShown(Tab tab, Dialog dialog) {
        DialogModalState dialogModalState = new DialogModalState(String.format("%s dialog with message %s", dialog.type(), dialog.message()), dialog);
        setModalState(dialogModalState, tab);

        Optional.of(pendingAction).ifPresent(action -> {
            try {
                action.getDialogShown().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String outputFile(FullConfig config, String name) throws IOException {
        Files.createDirectories(Path.of(config.getOutputDir()));
        String fileName = sanitizeForFilePath(name);
        return Path.of(config.getOutputDir(), fileName).toString();
    }

    public String sanitizeForFilePath(String s) {
        int separatorIndex = s.lastIndexOf('.');
        if (separatorIndex == -1) {
            return sanitize(s);
        }

        String baseName = sanitize(s.substring(0, separatorIndex));
        String extension = sanitize(s.substring(separatorIndex + 1));

        return baseName + "." + extension;
    }

    private String sanitize(String s) {
        return s.replaceAll("[\\u0000-\\u002C\\u002E-\\u002F\\u003A-\\u0040\\u005B-\\u0060\\u007B-\\u007F]+", "-");
    }

    // 使用 Java 11 的 Files API 和 Path
    public CompletableFuture<Void> downloadStartedAsync(Tab tab, Download download) {
        return CompletableFuture.runAsync(() -> {
            try {

                var outputFile = config.getOutputFile(download.suggestedFilename());
                var entry = new DownloadEntry(download, false, outputFile);
                downloads.add(entry);

                // 使用 Path API 确保目录存在
                var outputPath = Path.of(outputFile);
                Files.createDirectories(outputPath.getParent());

                download.saveAs(outputPath);
                entry.setFinished(true);

            } catch (IOException e) {
                log.error("Error handling download", e);
                throw new RuntimeException("Failed to handle download", e);
            }
        });
    }

    public void downloadStarted(Tab tab, Download download) {
        try {
            DownloadEntry entry = new DownloadEntry();
            entry.setDownload(download);
            entry.setFinished(false);
            entry.setOutputFile(outputFile(config, download.suggestedFilename()));

            downloads.add(entry);
            download.saveAs(Path.of(entry.getOutputFile()));
            entry.setFinished(true);
        } catch (Exception e) {
            log.warn("Context#downloadStarted: Error handle download: ", e);
        }
    }

    private void onPageCreated(Page page) {
        Tab tab = new Tab(this, page, this::onPageClosed);
        tabs.add(tab);
        if (null == currentTab) {
            currentTab = tab;
        }
    }

    private void onPageClosed(Tab tab) {
        modalStates.removeIf(state -> state.getTab().equals(tab));
        int index = tabs.indexOf(tab);
        if (index == -1) {
            return;
        }
        tabs.remove(index);

        if (currentTab.equals(tab)) {
            currentTab = tabs.get(Math.min(index, tabs.size() - 1));
        }
        if (tabs.isEmpty()) {
            close();
        }
    }

    public CompletableFuture<Void> closeAsync() {
        return CompletableFuture.runAsync(() -> {
            if (null == browserContext) {
                return;
            }
            log.debug("Closing context");


            if (config.isSaveTrace()) {
                browserContext.tracing().stop();
            }
        });
    }

    public void close() {
        closeAsync().join();
    }

    private void setupRequestInterception(BrowserContext context) {
        if (CollectionUtils.isNotEmpty(this.config.getNetwork().getAllowedOrigins())) {
            context.route("**", route -> route.abort("blockedbyclient"));

            for (String origin : this.config.getNetwork().getAllowedOrigins()) {
                context.route(String.format("*://%s/**", origin), Route::resume);
            }
        }

        if (CollectionUtils.isNotEmpty(this.config.getNetwork().getBlockedOrigins())) {
            for (String origin : this.config.getNetwork().getBlockedOrigins()) {
                context.route(String.format("*://%s/**", origin), route -> route.abort("blockedbyclient"));
            }
        }
    }

    // 私有方法
    private void initializeExistingPages() {
        browserContext.pages().forEach(this::onPageCreated);
    }

    private void setupPageListener() {
        browserContext.onPage(this::onPageCreated);
    }

}
