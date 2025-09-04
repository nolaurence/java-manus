package cn.nolaurene.cms.common.dto.tc;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class FrontendTcNode {

    private Long id;

    /**
     * 辅助字段，只用于构建树
     */
    private String uid;

    private String parentUid;

    // 辅助字段，用于构建树
    private int sortOrder;

    @JSONField(name = "data")
    private NodeData data;

    @JSONField(name = "children")
    private List<FrontendTcNode> children;

    private Boolean isRoot;
}
