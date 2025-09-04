package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

import java.util.List;

/**
 * @author guofukang.gfk
 * @date 2024/11/4.
 */
@Data
public class ChangeAction {

    private String action;

    private FrontendTcNode node;

    private MoveData moveData;

    private List<FrontendTcNode> nodeList;

    private List<OrderData> orderData;
}

