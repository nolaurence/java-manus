package cn.nolaurene.cms.controller.tc;

import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.common.vo.tc.OSTreeNode;
import cn.nolaurene.cms.common.vo.tc.OSTreeSelectNode;
import cn.nolaurene.cms.service.OSService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author guofukang.gfk
 * @date 2024/11/1.
 */
@RestController
@RequestMapping("/organization")
public class OrganizationController {

    @Resource
    private OSService osService;

    @GetMapping("/{rootOrgId}/tree")
    public BaseWebResult<OSTreeNode> getOrganizationTree(@PathVariable Long rootOrgId) {
        OSTreeNode tree = osService.getOrganizationTree(rootOrgId);
        if (tree == null) {
            return BaseWebResult.fail("未找到根节点");
        }
        return BaseWebResult.success(tree);
    }

    @GetMapping("/{rootOrgId}/treeSelect")
    public BaseWebResult<List<OSTreeSelectNode>> getOrganizationTreeSelect(@PathVariable Long rootOrgId) {
        OSTreeNode tree = osService.getOrganizationTree(rootOrgId);
        if (tree == null) {
            return BaseWebResult.fail("未找到根节点");
        }

        OSTreeSelectNode selectRootNode = convertToTreeSelectNode(tree);
        return BaseWebResult.success(Collections.singletonList(selectRootNode));
    }

    private OSTreeSelectNode convertToTreeSelectNode(OSTreeNode treeNode) {
        OSTreeSelectNode treeSelectNode = new OSTreeSelectNode();
        treeSelectNode.setId(treeNode.getId());
        treeSelectNode.setTitle(treeNode.getTitle());
        treeSelectNode.setValue(treeNode.getKey());
        treeSelectNode.setDescription(treeNode.getDescription());
        if (CollectionUtils.isNotEmpty(treeNode.getChildren())) {
            treeSelectNode.setChildren(new ArrayList<>());
            for (OSTreeNode child : treeNode.getChildren()) {
                treeSelectNode.getChildren().add(convertToTreeSelectNode(child));
            }
        }
        return treeSelectNode;
    }
}
