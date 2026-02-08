package cn.nolaurene.cms.service.sandbox.backend.message;

import lombok.Data;

import java.util.List;

@Data
public class Step {

    private Integer id;
    private String description;
    private String status;
    private String result;
    private String error;
    private List<Long> toolIds;
}
