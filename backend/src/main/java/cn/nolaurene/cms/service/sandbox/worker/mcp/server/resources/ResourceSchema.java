package cn.nolaurene.cms.service.sandbox.worker.mcp.server.resources;

import lombok.Data;

@Data
public class ResourceSchema {

    private String uri;

    private String name;

    private String description;

    private String mimeType;
}
