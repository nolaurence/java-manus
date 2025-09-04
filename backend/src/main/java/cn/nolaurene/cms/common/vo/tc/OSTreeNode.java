package cn.nolaurene.cms.common.vo.tc;

import lombok.Data;

import java.util.List;

/**
 * @author guofukang.gfk
 * @date 2024/10/31.
 */
@Data
public class OSTreeNode {

    private Long id;

    private String title;

    private String key;

    private String description;

    private List<OSTreeNode> children;
}
