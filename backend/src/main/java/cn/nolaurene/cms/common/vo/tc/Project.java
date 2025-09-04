package cn.nolaurene.cms.common.vo.tc;

import lombok.Data;

import java.util.Date;

@Data
public class Project {
    private Long id;

    private String name;

    private String creatorName;

    private Date modifyTime;

    private Long caseRootId;

    private String caseRootUid;
}
