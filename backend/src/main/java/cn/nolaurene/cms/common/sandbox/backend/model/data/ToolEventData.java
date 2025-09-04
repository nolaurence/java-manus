package cn.nolaurene.cms.common.sandbox.backend.model.data;

import lombok.Data;

import java.util.Map;

@Data
public class ToolEventData {

    private long timestamp;

    private String name;

    private String function;

    private Map<String, Object> args;
}
