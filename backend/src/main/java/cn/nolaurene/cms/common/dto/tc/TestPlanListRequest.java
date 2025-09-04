package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

@Data
public class TestPlanListRequest {

    private String planName;

    private String creatorName;

    private Long organizationId;

    private Integer current;

    private Integer pageSize;
}
