package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

@Data
public class TagRequest {

    private int current;

    private int pageSize;

    private String tagName;

    private String creatorName;
}
