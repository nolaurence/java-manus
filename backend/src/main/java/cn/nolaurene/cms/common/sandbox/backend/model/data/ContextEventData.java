package cn.nolaurene.cms.common.sandbox.backend.model.data;

import lombok.Data;

@Data
public class ContextEventData {

    private long timestamp;

    private int usedTokens;

    private int maxTokens;

    private int percent;

    private Boolean compacted;
}
