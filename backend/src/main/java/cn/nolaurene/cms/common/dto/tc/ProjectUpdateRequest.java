package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectUpdateRequest extends ProjectCreateRequest {

    private Long id;
}
