package cn.nolaurene.cms.common.vo.tc;

import lombok.Data;

import java.util.List;

@Data
public class OSTreeSelectNode {

    private Long id;

    private String title;

    private String value;

    private String description;

    private List<OSTreeSelectNode> children;
}
