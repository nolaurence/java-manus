package cn.nolaurene.cms.service.sandbox.worker.mcp.snapshot;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class PlaywrightAriaSnapshot {

    protected final Page page;

    public PlaywrightAriaSnapshot(Page page) {
        this.page = page;
    }


    /**
     * 获取页面的 ARIA 快照，专为 AI 优化
     */
    public String snapshotForAI() {
        String script = readAriaSnapshotScript();
        return (String) page.evaluate(script);
    }

    public String readAriaSnapshotScript() {
        try {
            ClassPathResource resource = new ClassPathResource("ariaSnapshot.js");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("无法读取ariaSnapshot.js", e);
        }
    }

    /**
     * 获取包含所有框架的 ARIA 快照
     */
    public String snapshotForAIWithFrames() {
        List<String> allSnapshots = new ArrayList<>();

        // 获取主框架快照
        String mainSnapshot = snapshotFrame(page.mainFrame(), 0);
        allSnapshots.add(mainSnapshot);

        // 处理子框架
        List<Frame> childFrames = page.frames();
        for (int i = 1; i < childFrames.size(); i++) {
            try {
                String childSnapshot = snapshotFrame(childFrames.get(i), i);
                allSnapshots.add("  // Frame " + i + ":");
                allSnapshots.add(indentLines(childSnapshot, "  "));
            } catch (Exception e) {
                System.err.println("Failed to snapshot frame " + i + ": " + e.getMessage());
            }
        }

        return String.join("\n", allSnapshots);
    }

    /**
     * 获取制定框架的 ARIA 快照
     */
    private String snapshotFrame(Frame frame, int frameOrdinal) {
        try {
            Object result = frame.evaluate("" +
                    "(frameOrdinal) => {\n" +
                    "    if (window.__injectedScript) {\n" +
                    "        return window.__injectedScript.ariaSnapshot(document.body, { \n" +
                    "            forAI: true, \n" +
                    "            refPrefix: frameOrdinal ? 'f' + frameOrdinal : '' \n" +
                    "        });\n" +
                    "    }\n" +
                    "\n" +
                    "    // 基础实现（这里可以复用上面的 generateAriaSnapshot 函数）\n" +
                    "    return 'Frame ' + frameOrdinal + ' content';\n" +
                    "}", frameOrdinal);

            return result != null ? result.toString() : "";
        } catch (Exception e) {
            System.err.println("Error getting frame snapshot: " + e.getMessage());
            return "";
        }
    }

    /**
     * 为文本行添加缩进
     */
    private String indentLines(String text, String indent) {
        return String.join("\n",
                text.lines()
                        .map(line -> indent + line)
                        .toArray(String[]::new));
    }

    /**
     * 等待页面稳定后获取快照
     */
    public String snapshotForAIWhenStable() {
        // 等待页面加载完成
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // 等待一小段时间确保动态内容加载
        page.waitForTimeout(500);

        return snapshotForAI();
    }

    /**
     * 获取特定元素的 ARIA 快照
     */
    public String snapshotElement(String selector) {
        ElementHandle element = page.querySelector(selector);
        if (element == null) {
            throw new RuntimeException("Element not found: " + selector);
        }

        return (String) element.evaluate("" +
                "(element) => {\n" +
                "    if (window.__injectedScript) {\n" +
                "        return window.__injectedScript.ariaSnapshot(element, { \n" +
                "            forAI: true, \n" +
                "            refPrefix: '' \n" +
                "        });\n" +
                "    }\n" +
                "\n" +
                "    // 基础实现\n" +
                "    return 'Element: ' + element.tagName.toLowerCase();\n" +
                "}", element);
    }

    /**
     * 检查是否支持 ariaSnapshot
     */
    public boolean isAriaSnapshotSupported() {
        return (Boolean) page.evaluate("() => !!window.__injectedScript?.ariaSnapshot");
    }
}
