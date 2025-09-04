package cn.nolaurene.cms.common.sandbox.backend.model.data;

import lombok.Data;

@Data
public class MessageEventData {

    private long timestamp;

    private String content;

    private String contentDelta;

    private String reasoningContent;

    private String reasoningContentDelta;

    private long thinkTime;
}
