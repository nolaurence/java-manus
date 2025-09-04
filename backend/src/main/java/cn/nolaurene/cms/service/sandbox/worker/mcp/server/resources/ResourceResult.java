package cn.nolaurene.cms.service.sandbox.worker.mcp.server.resources;

import lombok.Data;

@Data
public class ResourceResult {

    private String uri;

    private String mimeType;

    private String text;

    private String blob;
}
