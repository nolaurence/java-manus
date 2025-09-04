package cn.nolaurene.cms.service.sandbox.worker.browser;


import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.common.sandbox.worker.req.mcp.MCPCommand;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nolau
 * @date 2025/6/15
 * @description
 */
@RestController
@RequestMapping("/worker/mcp")
@Tag(name = "sandbox worker - mcp controller")
public class MCPController {

    /**
     * 包含playwright 和 浏览器实例的服务
     */
    @Resource
    private BrowserService browserService;

    private final Map<String, BrowserContext> contextMap = new ConcurrentHashMap<>();
//    private final Map<String, Page>

    @PostMapping("/control")
    public Response<?> control(@RequestParam String sessionId, @RequestBody MCPCommand cmd) {
        try {
            Page page = browserService.getOrCreatePage(sessionId);
            switch (cmd.getAction()) {
                case "navigate":
                    page.navigate(cmd.getValue());
                    // 等待页面加载完成
                    browserService.waitForLoadState(sessionId);
                    return Response.success("Navigated to " + cmd.getValue());
                case "click":
                    page.click(cmd.getSelector());
                    return Response.success("Clicked: " + cmd.getValue());
                case "fill":
                    page.fill(cmd.getSelector(), cmd.getValue());
                    return Response.success("Filled: " + cmd.getValue());
                case "type":
                    page.type(cmd.getSelector(), cmd.getValue());
                    return Response.success("Typed: " + cmd.getValue());
                case "screenshot":
                    String base64 = browserService.screenshotBase64(sessionId);
                    return Response.success(base64);
                case "content":
                    return Response.success(page.content());
                default:
                    return Response.error("Unknown action: " + cmd.getAction(), cmd);
            }
        } catch (Exception e) {
            return Response.error("Error executing command: " + e.getMessage(), 500);
        }
    }

    @GetMapping("/sessions")
    public Response<?> sessions() {
        return Response.success(browserService.getActiveSessions());
    }

    @DeleteMapping("/session/{sessionId}")
    public Response<String> deleteSession(@PathVariable String sessionId) {
        browserService.closeSession(sessionId);
        return Response.success("Session closed: " + sessionId);
    }
}
