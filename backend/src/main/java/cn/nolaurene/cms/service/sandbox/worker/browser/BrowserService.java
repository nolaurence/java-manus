package cn.nolaurene.cms.service.sandbox.worker.browser;

import com.alibaba.fastjson.JSON;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Date: 2025/5/27
 * Author: nolaurence
 * Description:
 */
@Slf4j
@Service
public class BrowserService {

    private Playwright playwright;

    @Getter
    private Browser browser;

    private final ConcurrentHashMap<String, BrowserContext> contextMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Page> pageMap = new ConcurrentHashMap<>();

    private static final List<String> chromiumArgs = List.of(
            "--display=:1",
            "--window-size=1280,1024",
            "--start-maximized",
            "--window-position=0,0",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-setuid-sandbox",
            "--disable-accelerated-2d-canvas",
            "--disable-gpu",
            "--disable-features=WelcomeExperience,SigninPromo",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-infobars",
            "--test-type",
            "--disable-popup-blocking",
            "--disable-gpu-sandbox",
            "--no-xshm",
            "--new-window=false",
            "--disable-notifications",
            "--disable-extensions",
            "--disable-component-extensions-with-background-pages",
            "--disable-prompt-on-repost",
            "--disable-dialogs",
            "--disable-modal-dialogs",
            "--disable-web-security",
            "--disable-site-isolation-trials",
            "--remote-debugging-address=0.0.0.0",
            "--remote-debugging-port=8222",
            "--enable-logging"
    );

    public void startBrowser() throws InterruptedException {
        Map<String, String> env = new HashMap<>(System.getenv());
        log.info("[BrowserService#startBrowser] Starting browser with environment variables: {}", JSON.toJSONString(env));
        env.put("DISPLAY", ":1");
        env.put("DEBUG", "pw:browser*");
        env.put("CHROME_LOG_FILE", "/app/log/chromium.log"); // 如需输出到文件可加此行
        playwright = Playwright.create();

        // 启动无头模式的 Chromium
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setExecutablePath(Path.of("/usr/bin/chromium"))
                .setArgs(chromiumArgs)
                .setHeadless(false)
                .setEnv(env);

        // browser process started but window not visible
        browser = playwright.chromium().launch(options);

        // 做演示用
//        Page page = browser.newPage();
//        page.navigate("https://baidu.com");
//        Thread.sleep(1500);
    }

    public Page getOrCreatePage(String sessionId) {
        if (!pageMap.containsKey(sessionId) && !contextMap.containsKey(sessionId)) {
            // 设置分辨率
            Browser.NewContextOptions newContextOptions = new Browser.NewContextOptions().setViewportSize(1280, 1024);
            BrowserContext context = browser.newContext(newContextOptions);
            Page page = context.newPage();
            contextMap.put(sessionId, context);
            pageMap.put(sessionId, page);
        }
        return pageMap.get(sessionId);
    }

    public BrowserContext createNewContext(String sessionId) {
        if (!contextMap.containsKey(sessionId)) {
            // resolution should be explicitly set for each context
            Browser.NewContextOptions newContextOptions = new Browser.NewContextOptions().setViewportSize(1280, 1024);
            BrowserContext context = browser.newContext(newContextOptions);
            contextMap.put(sessionId, context);
            log.info("[BrowserService#createNewContext] Created new context for session: {}", sessionId);
            return context;
        }
        log.warn("[BrowserService#createNewContext] Context already exists for session: {}", sessionId);
        return contextMap.get(sessionId);
    }

    public void closeSession(String sessionId) {
        try {
            Page page = pageMap.remove(sessionId);
            if (page != null) {
                page.close();
            }
            BrowserContext context = contextMap.remove(sessionId);
            if (context != null) {
                context.close();
            }
        } catch (Exception e) {
            log.warn("[BrowserService#closeSession] Error closing session {}: {}", sessionId, e.getMessage());
        }
    }

    // 关闭浏览器
    public void closeBrowser() {
        for (String sessionId : pageMap.keySet()) {
            closeSession(sessionId);
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    public Set<String> getActiveSessions() {
        return pageMap.keySet();
    }


    public String screenshotBase64(String sessionId) {
        Page page = getOrCreatePage(sessionId);
        byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        return Base64.getEncoder().encodeToString(screenshotBytes);
    }

    public void waitForLoadState(String sessionId) {
        Page page = getOrCreatePage(sessionId);
        page.waitForLoadState(LoadState.LOAD);
    }

//    public static void main(String[] args) {
//        new BrowserContext();
//
//    }
}
