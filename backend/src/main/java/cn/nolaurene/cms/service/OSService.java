package cn.nolaurene.cms.service;

import cn.nolaurene.cms.common.vo.tc.OSTreeNode;
import cn.nolaurene.cms.dal.enhance.mapper.OrganizationStructureEnhanceMapper;
import cn.nolaurene.cms.dal.entity.OrganizationStructureDO;
import cn.nolaurene.cms.dal.mapper.OrganizationStructureMapper;
import cn.nolaurene.cms.exception.BusinessException;
import io.mybatis.mapper.example.Example;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author guofukang.gfk
 * @date 2024/11/1.
 */
@Service
public class OSService {

    @Resource
    private OrganizationStructureEnhanceMapper organizationStructureEnhanceMapper;

    @Resource
    private OrganizationStructureMapper organizationStructureMapper;

    public OSTreeNode getOrganizationTree(long rootOrgId) {
        Example<OrganizationStructureDO> example = new Example<>();
        Example.Criteria<OrganizationStructureDO> criteria = example.createCriteria();
        criteria.andEqualTo(OrganizationStructureDO::getIsDeleted, false);

        List<OrganizationStructureDO> nodes = organizationStructureMapper.selectByExample(example);

        if (nodes.isEmpty()) {
            throw new BusinessException("未找到根节点");
        }

        OSTreeNode root = findRootNode(nodes, rootOrgId);
        setChildren(root, nodes);
        return root;
    }

    private OSTreeNode findRootNode(List<OrganizationStructureDO> nodes, long rootOrgId) {
        return nodes.stream()
                .filter(node -> node.getOrgId() == rootOrgId)
                .map(this::convertToTreeNode)
                .findFirst()
                .orElse(null);
    }

    private void setChildren(OSTreeNode parentNode, List<OrganizationStructureDO> allNodes) {
        List<OSTreeNode> children = allNodes.stream()
                .filter(node -> node.getParentId() != null && node.getParentId().equals(parentNode.getId()))
                .map(this::convertToTreeNode)
                .collect(Collectors.toList());

        children.forEach(child -> setChildren(child, allNodes));
        parentNode.setChildren(children);
    }

    private OSTreeNode convertToTreeNode(OrganizationStructureDO dataObject) {
        OSTreeNode node = new OSTreeNode();
        node.setId(dataObject.getOrgId());
        node.setTitle(dataObject.getName());
        node.setKey(dataObject.getOrgId().toString());
        node.setDescription(dataObject.getDescription());
        return node;
    }
}
