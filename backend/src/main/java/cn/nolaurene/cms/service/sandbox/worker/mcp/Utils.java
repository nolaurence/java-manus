package cn.nolaurene.cms.service.sandbox.worker.mcp;


import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/7/7 下午4:18
 * @description:
 */
@Slf4j
public class Utils {


    /**
     * 等待操作完成，监控网络请求和页面导航
     */
    public static <T> CompletableFuture<T> waitForCompletion(
            Context context,
            Tab tab,
            Supplier<CompletableFuture<T>> callback) {

        Set<Request> requests = ConcurrentHashMap.newKeySet();
        CompletableFuture<Void> barrier = new CompletableFuture<>();

        // 完成条件检查
        Runnable checkCompletion = () -> {
            if (requests.isEmpty()) {
                barrier.complete(null);
            }
        };

        // 监听器们
//        tab.getPage().on("request", requests::add);
        tab.getPage().onRequest(requests::add);
        tab.getPage().onRequestFinished(req -> {
            requests.remove(req);
            checkCompletion.run();
        });
        tab.getPage().onFrameNavigated(frame -> {
            if (frame.parentFrame() == null) {
                tab.waitForLoadState(LoadState.LOAD, null);
                barrier.complete(null);
            }
        });

        return callback.get()
                .thenCompose(result -> {
                    checkCompletion.run(); // 检查是否可以立即完成
                    return barrier.orTimeout(10, TimeUnit.SECONDS)
                            .thenCompose(v -> {
                                try {
                                    context.waitForTimeout(1000);
                                } catch (InterruptedException e) {
                                    log.error("wait for completion error: ", e);
                                }
                                return null;
                            })
                            .thenApply(v -> result);
                });
    }

//    public static String generateLocator(Locator locator) {
//        try {
//            locator.
//        }
//    }
}
