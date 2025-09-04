package cn.nolaurene.cms.common.sandbox.backend.model.data;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PlanEventData {

    private String id;

    private String title;

    private String goal;

    private List<StepEventData> steps;

    private String message;

    private String status;

    private Map<String, Object> result;

    private String error;
}
