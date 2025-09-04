package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

@Data
public class ProjectCreateRequest {

    private String name;

    private Long organizationId;
}
