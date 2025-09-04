package cn.nolaurene.cms.service.sandbox.worker.mcp.server;

import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import lombok.Data;

@Data
public class RequestInfo {

    private String url;

    private String method;

    private int status;

    private int respStatus;

    private String respStatusText;

    public RequestInfo(Request request, Response response) {
        this.url = request.url();
        this.method = request.method();
        this.status = response != null ? response.status() : -1;
        this.respStatus = response != null ? response.status() : -1;
        this.respStatusText = response != null ? response.statusText() : null;
    }
}
