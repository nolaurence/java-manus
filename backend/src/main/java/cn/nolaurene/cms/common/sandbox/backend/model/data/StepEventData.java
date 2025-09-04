package cn.nolaurene.cms.common.sandbox.backend.model.data;

import lombok.Data;

@Data
public class StepEventData {

    private long timestamp;

    private String status;

    private String id;

    private String description;

    private String result;
}
