package cn.nolaurene.cms.controller.tc;

import cn.nolaurene.cms.common.dto.tc.ChangeAction;
import cn.nolaurene.cms.common.dto.tc.FrontendTcNode;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.tc.TcNodeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/tc")
public class TestCaseController {

    @Resource
    private TcNodeService tcNodeService;

    /**
     * 给导入的时候用
     * @param node
     * @return
     */
    @PostMapping("/save")
    public BaseWebResult<Boolean> saveFullData(@RequestBody FrontendTcNode node) {
        boolean result = tcNodeService.saveTree(node);
        return BaseWebResult.success(result);
    }

    @GetMapping("/get")
    public BaseWebResult<FrontendTcNode> getTestCaseTree(@RequestParam String rootUid) {
        FrontendTcNode result = tcNodeService.buildTestCaseTree(rootUid);
        return BaseWebResult.success(result);
    }

    @PostMapping("/ops")
    public BaseWebResult<Boolean> nodeOperate(@RequestBody ChangeAction changeAction) {
        Boolean result;
        switch (changeAction.getAction()) {
            case "add":
                result = tcNodeService.addNode(changeAction);
                break;
            case "delete":
                result = tcNodeService.deleteNode(changeAction);
                break;
            case "update":
                result = tcNodeService.updateNode(changeAction);
                break;
            case "move":
                result = tcNodeService.moveNode(changeAction);
                break;
            case "batchAdd":
                result = tcNodeService.batchAddNode(changeAction);
                break;
            case "batchDelete":
                result = tcNodeService.batchDeleteNode(changeAction);
                break;
            default:
                throw new BusinessException("不支持的操作");
        }
        return BaseWebResult.success(result);
    }
}
