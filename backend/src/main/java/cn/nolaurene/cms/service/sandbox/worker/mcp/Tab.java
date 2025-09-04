package cn.nolaurene.cms.service.sandbox.worker.mcp;

import cn.nolaurene.cms.service.sandbox.worker.mcp.server.FileUploadModalState;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalState;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.RequestInfo;
import cn.nolaurene.cms.service.sandbox.worker.mcp.snapshot.PageSnapshot;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
public class Tab {

    @Getter
    private Context context;
    @Getter
    private final Page page;
    private List<ConsoleMessage> consoleMessages;
    private final Map<String, RequestInfo> requests;
    private PageSnapshot snapshot;
    private Consumer<Tab> onPageClose;

    public Tab(Context context, Page page, Consumer<Tab> onPageClose) {
        this.context = context;
        this.page = page;
        this.consoleMessages = new ArrayList<>();
        this.requests = new HashMap<>();
        this.onPageClose = onPageClose;

        page.onConsoleMessage(event -> consoleMessages.add(event));
        page.onRequest(request -> {
            if (null != request) {
                requests.put(genUniqueRequestId(request), new RequestInfo(request, null));
            }
        });
        page.onResponse(response -> {
            if (null != response) {
                requests.put(genUniqueRequestId(response.request()), new RequestInfo(response.request(), response));
            }
        });
        page.onClose(pageObj -> onClose());
        page.onFileChooser(chooser -> {
            ModalState modalState = new FileUploadModalState("File chooser", chooser);
            context.setModalState(modalState, this);
        });
        page.onDialog(dialog -> context.dialogShown(this, dialog));
        page.onDownload(download -> context.downloadStarted(this, download));
        page.setDefaultNavigationTimeout(60000);
        page.setDefaultTimeout(60000);
    }

    public String title() {
        return page.title();
    }

    public void waitForLoadState(LoadState state, Double timeout) {
        try {
            page.waitForLoadState(state, new Page.WaitForLoadStateOptions().setTimeout(timeout != null ? timeout : 60000L));
        } catch (PlaywrightException ex) {
            log.warn("Tab#waitForLoadState: title {} waitForLoadState {} timeout", title(), state, ex);
        }
    }

    public void navigate(String url) {
        // 清理采集的 artifact（如有需要可以自定义此函数）
        clearCollectedArtifacts();
        AtomicReference<Download> downloadRef = new AtomicReference<>();
        page.onDownload(download -> downloadRef.set(download));

        try {
            Page.NavigateOptions options = new Page.NavigateOptions();
            options.setWaitUntil(WaitUntilState.DOMCONTENTLOADED);
            page.navigate(url, options);
            page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(60000));
        } catch (PlaywrightException ex) {
            String message = ex.getMessage();
            boolean mightBeDownload = message.contains("net::ERR_ABORTED") || message.contains("Download is starting");

            if (!mightBeDownload) {
                throw ex;
            }

            // 等待下载事件触发
            try {
                Runnable runnable = () -> {};
                Download download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(1000), runnable);
                if (download != null) {
                    // 处理下载
                    log.info("Download started: " + download.url());
                } else {
                    throw ex;
                }
            } catch (PlaywrightException ignored) {
                throw ex;
            }
        } finally {
            // 清理下载引用
            page.offDownload(download -> {});
        }
    }

    public boolean hasSnapshot() {
        return snapshot != null;
    }

    public PageSnapshot snapshotOrDie() {
        if (null == snapshot) {
            throw new IllegalArgumentException("No snapshot available");
        }
        return snapshot;
    }

    public List<ConsoleMessage> consoleMessages() {
        return consoleMessages;
    }

    public Map<String, RequestInfo> requests() {
        return requests;
    }

    public void captureSnapshot() {
        snapshot = PageSnapshot.create(page);
    }

    private void clearCollectedArtifacts() {
        consoleMessages.clear();
        requests.clear();
    }

    private void onClose() {
        clearCollectedArtifacts();
        onPageClose.accept(this);
    }

    private String genUniqueRequestId(Request request) {
        if (null == request) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(request.method()).append("|");
        sb.append(request.url()).append("|");
        Map<String, String> headers = request.headers();
        // 可选：只选部分关键头
        sb.append(headers.getOrDefault("content-type", "")).append("|");
        String postData = request.postData();
        if (postData != null) {
            sb.append(postData);
        }
        if (null != request.timing()) {
            sb.append("|").append(request.timing().startTime);
        }
        return Integer.toHexString(sb.toString().hashCode());
    }
}
