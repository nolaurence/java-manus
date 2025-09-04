package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

/**
 * @author guofukang.gfk
 * @date 2024/12/23.
 */
@Data
public class TestPlanCreateRequest {

    private String planName;

    private String creatorName;

    private Long organizationId;
}
