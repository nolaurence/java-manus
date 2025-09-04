package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

@Data
public class ProjectsRequest {

    private String name;

    private String creatorName;

    private Long organizationId;

    private Boolean onlyMe;

    private Integer current;

    private Integer pageSize;
}
