package cn.nolaurene.cms.common.sandbox.backend.model;

import lombok.Data;

import java.util.Map;

@Data
public class ToolCall {

    private int index;

    private String id;

    private String type;

    private Function function;
}
