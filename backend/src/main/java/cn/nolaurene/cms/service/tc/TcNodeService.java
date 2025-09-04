package cn.nolaurene.cms.service.tc;

import cn.nolaurene.cms.common.constants.TagConstants;
import cn.nolaurene.cms.common.dto.tc.*;
import cn.nolaurene.cms.common.vo.tc.StyledTag;
import cn.nolaurene.cms.common.vo.tc.TagVO;
import cn.nolaurene.cms.dal.enhance.mapper.TestCaseEnhancedMapper;
import cn.nolaurene.cms.dal.entity.CaseTestCaseDO;
import cn.nolaurene.cms.dal.mapper.CaseTestCaseMapper;
import cn.nolaurene.cms.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.mybatis.mapper.example.Example;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Case;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TcNodeService {

    @Resource
    private CaseTestCaseMapper testCaseMapper;

    @Resource
    private TestCaseEnhancedMapper testCaseEnhancedMapper;

    /**
     * 添加节点 add move delete update
     */
    @Transactional
    public boolean addNode(ChangeAction changeAction) {
        if (StringUtils.isBlank(changeAction.getAction()) || !changeAction.getAction().equals("add")) {
            return false;
        }
        FrontendTcNode node = changeAction.getNode();
        CaseTestCaseDO nodeToAdd = convertToDataObject(node);

        // 捞取父节点
        Example<CaseTestCaseDO> parentExample = new Example<>();
        Example.Criteria<CaseTestCaseDO> parentCriteria = parentExample.createCriteria();
        parentCriteria.andEqualTo(CaseTestCaseDO::getUid, node.getParentUid());
        List<CaseTestCaseDO> parentCaseTestCaseDOS = testCaseMapper.selectByExample(parentExample);
        if (CollectionUtils.isEmpty(parentCaseTestCaseDOS)) {
            throw new BusinessException("父节点不存在，请刷新页面");
        }

        // 确定新节点的排序值
        Example<CaseTestCaseDO> siblingExample = new Example<>();
        siblingExample.createCriteria()
                .andEqualTo(CaseTestCaseDO::getParentUid, node.getParentUid())
                .andEqualTo(CaseTestCaseDO::getIsDeleted, false);
        List<CaseTestCaseDO> siblings = testCaseMapper.selectByExample(siblingExample);

        int maxSortOrder = siblings.stream()
                .mapToInt(CaseTestCaseDO::getSortOrder)
                .max()
                .orElse(0);
        nodeToAdd.setSortOrder(maxSortOrder + 1); // 设置排序值

        Example<CaseTestCaseDO> example = new Example<>();
        Example.Criteria<CaseTestCaseDO> criteria = example.createCriteria();
        criteria.andEqualTo(CaseTestCaseDO::getUid, nodeToAdd.getUid());

        // 数据库不支持唯一键时，需要先查询是否存在 - -，无语
        List<CaseTestCaseDO> caseTestCaseDOS = testCaseMapper.selectByExample(example);
        if (!caseTestCaseDOS.isEmpty()) {
            // 判断是否删除
            if (caseTestCaseDOS.get(0).getIsDeleted()) {
                nodeToAdd.setIsDeleted(false);
                nodeToAdd.setId(caseTestCaseDOS.get(0).getId());
                int updateResult = testCaseMapper.updateByPrimaryKeySelective(nodeToAdd);
                return updateResult > 0;
            }
            throw new BusinessException("节点已存在");
        }

        // 插入父节点id 并插入
        nodeToAdd.setProjectId(parentCaseTestCaseDOS.get(0).getProjectId());
        nodeToAdd.setParentUid(parentCaseTestCaseDOS.get(0).getUid());
        int insertResult = testCaseMapper.insertSelective(nodeToAdd);

        updateOrder(changeAction.getOrderData(), Collections.singletonList(nodeToAdd.getUid()));

        return insertResult > 0;
    }

    /**
     * 移动节点
     */
    @Transactional
    public boolean moveNode(ChangeAction changeAction) {
        Example<CaseTestCaseDO> example = new Example<>();
        Example.Criteria<CaseTestCaseDO> criteria = example.createCriteria();
        criteria.andEqualTo(CaseTestCaseDO::getUid, changeAction.getMoveData().getNodeUid());
        criteria.andEqualTo(CaseTestCaseDO::getIsDeleted, false);
        List<CaseTestCaseDO> caseTestCaseDOS = testCaseMapper.selectByExample(example);

        // 拿到要移动的节点
        if (CollectionUtils.isEmpty(caseTestCaseDOS)) {
            throw new BusinessException("移动的节点不存在，请刷新页面");
        }
        CaseTestCaseDO nodeToMove = caseTestCaseDOS.get(0);

        // 查询目标父节点的子节点
        Example<CaseTestCaseDO> targetExample = new Example<>();
        targetExample.createCriteria()
                .andEqualTo(CaseTestCaseDO::getParentUid, changeAction.getMoveData().getTo())
                .andEqualTo(CaseTestCaseDO::getIsDeleted, false);
        List<CaseTestCaseDO> targetSiblings = testCaseMapper.selectByExample(targetExample);

        // 默认在最后面
        int newSortOrder = targetSiblings.stream()
                .mapToInt(CaseTestCaseDO::getSortOrder)
                .max()
                .orElse(0) + 1;

        // 拿到移动前后的父节点
        Example<CaseTestCaseDO> prevExample = new Example<>();
        Example.Criteria<CaseTestCaseDO> prevCriteria = prevExample.createCriteria();
        prevCriteria.andEqualTo(CaseTestCaseDO::getUid, changeAction.getMoveData().getFrom());
        List<CaseTestCaseDO> prevCaseTestCaseDOS = testCaseMapper.selectByExample(prevExample);

        Example<CaseTestCaseDO> nextExample = new Example<>();
        Example.Criteria<CaseTestCaseDO> nextCriteria = nextExample.createCriteria();
        nextCriteria.andEqualTo(CaseTestCaseDO::getUid, changeAction.getMoveData().getTo());
        nextCriteria.andEqualTo(CaseTestCaseDO::getIsDeleted, false);
        List<CaseTestCaseDO> targetCaseTestCaseDOS = testCaseMapper.selectByExample(nextExample);

        if (CollectionUtils.isEmpty(prevCaseTestCaseDOS) || CollectionUtils.isEmpty(targetCaseTestCaseDOS)) {
            throw new BusinessException("父节点不存在，请刷新页面");
        }
        if (prevCaseTestCaseDOS.get(0).getIsDeleted() || targetCaseTestCaseDOS.get(0).getIsDeleted()) {
            throw new BusinessException("父节点不存在，请刷新页面");
        }
        CaseTestCaseDO targetCaseTestCaseDO = targetCaseTestCaseDOS.get(0);

        nodeToMove.setParentUid(targetCaseTestCaseDO.getUid());
        nodeToMove.setSortOrder(newSortOrder);
        int updateResult = testCaseMapper.updateByPrimaryKeySelective(nodeToMove);

        updateOrder(changeAction.getOrderData(), Collections.singletonList(nodeToMove.getUid()));

        return updateResult > 0;
    }

    /**
     * 更新节点
     */
    @Transactional
    public boolean updateNode(ChangeAction changeAction) {
        FrontendTcNode node = changeAction.getNode();

        Example<CaseTestCaseDO> example = new Example<>();
        Example.Criteria<CaseTestCaseDO> criteria = example.createCriteria();
        criteria.andEqualTo(CaseTestCaseDO::getUid, node.getData().getUid());
        List<CaseTestCaseDO> caseTestCaseDOS = testCaseMapper.selectByExample(example);

        if (CollectionUtils.isEmpty(caseTestCaseDOS)) {
            throw new BusinessException("更新的节点不存在，请刷新页面");
        }
        if (caseTestCaseDOS.get(0).getIsDeleted()) {
            throw new BusinessException("更新的节点不存在，请刷新页面");
        }

        CaseTestCaseDO nodeToUpdate = caseTestCaseDOS.get(0);
        // 更新数据
        // 暂时只更新标签和名称
        nodeToUpdate.setName(node.getData().getText());
        nodeToUpdate.setTags(JSON.toJSONString(node.getData().getTag()));

        // TODO: 图片的持久化，image， imageSize， imageTitle这几个字段
        // 处理图片
        JSONObject extension = new JSONObject();

        if (StringUtils.isNotBlank(node.getData().getImage())) {
            extension.put("image", node.getData().getImage());
            if (null != node.getData().getImageSize()) {
                JSONObject imageSize = new JSONObject();
                imageSize.put("width", node.getData().getImageSize().getWidth());
                imageSize.put("height", node.getData().getImageSize().getHeight());
                imageSize.put("custom", node.getData().getImageSize().getCustom());
                extension.put("imageSize", imageSize);
            }
        } else {
            extension.put("image", null);
            JSONObject imageSize = new JSONObject();
            imageSize.put("custom", false);
            extension.put("imageSize", imageSize);
        }
        nodeToUpdate.setExtension(extension.toJSONString());

        int updateResult = testCaseMapper.updateByPrimaryKeySelective(nodeToUpdate);

        updateOrder(changeAction.getOrderData(), Collections.singletonList(nodeToUpdate.getUid()));

        return updateResult > 0;
    }

    /**
     * 删除节点
     */
    public boolean deleteNode(ChangeAction changeAction) {
        FrontendTcNode node = changeAction.getNode();

        Example<CaseTestCaseDO> example = new Example<>();
        Example.Criteria<CaseTestCaseDO> criteria = example.createCriteria();
        criteria.andEqualTo(CaseTestCaseDO::getUid, node.getData().getUid());
        List<CaseTestCaseDO> caseTestCaseDOS = testCaseMapper.selectByExample(example);

        if (CollectionUtils.isEmpty(caseTestCaseDOS)) {
            throw new BusinessException("更新的节点不存在，请刷新页面");
        }
        if (caseTestCaseDOS.get(0).getIsDeleted()) {
            return true;
        }

        CaseTestCaseDO caseTestCaseDO = caseTestCaseDOS.get(0);
        // 更新数据
        caseTestCaseDO.setIsDeleted(true);

        int updateResult = testCaseMapper.updateByPrimaryKeySelective(caseTestCaseDO);
        return updateResult > 0;
    }

    /**
     * 根据根节点 ID 构建测试用例树
     *
     * @param rootUid 根节点 UID
     * @return 构造的测试用例树结构
     */
    public FrontendTcNode buildTestCaseTree(String rootUid) {
        // 使用rootId捞取根节点
        Example<CaseTestCaseDO> rootExample = new Example<>();
        Example.Criteria<CaseTestCaseDO> rootCriteria = rootExample.createCriteria();
        rootCriteria.andEqualTo(CaseTestCaseDO::getUid, rootUid);
        rootCriteria.andEqualTo(CaseTestCaseDO::getIsDeleted, false);
        List<CaseTestCaseDO> rootNodeList = testCaseMapper.selectByExample(rootExample);

        if (rootNodeList.isEmpty() || !rootNodeList.get(0).getUid().equals(rootUid)) {
            throw new BusinessException("根节点不存在");
        }

        // 根据projectId捞取所有用例
        Example<CaseTestCaseDO> example = new Example<>();
        Example.Criteria<CaseTestCaseDO> criteria = example.createCriteria();
        criteria.andEqualTo(CaseTestCaseDO::getProjectId, rootNodeList.get(0).getProjectId());
        criteria.andEqualTo(CaseTestCaseDO::getIsDeleted, false);

        // 捞取所有用例节点
        List<CaseTestCaseDO> caseList = testCaseMapper.selectByExample(example);

        // 将每个节点按 ID 映射
        Map<String, FrontendTcNode> nodeMap = caseList.stream()
                .sorted(Comparator.comparingInt(CaseTestCaseDO::getSortOrder))
                .map(this::convertToNode)
                .collect(Collectors.toMap(FrontendTcNode::getUid, node -> node));

        // 构造树结构
        FrontendTcNode root = nodeMap.get(rootUid);
        for (FrontendTcNode node : nodeMap.values()) {
            String parentUid = node.getParentUid();
            if (parentUid != null && nodeMap.containsKey(parentUid)) {
                nodeMap.get(parentUid).getChildren().add(node);
            }
        }

        // 排序
        sortChildren(root);

        return root; // 返回构造完成的树结构
    }

    public boolean saveTree(FrontendTcNode rootNode) {

        // 拉去根节点的前置步骤
        if (StringUtils.isEmpty(rootNode.getParentUid())) {
            throw new BusinessException("根节点 parentUid 不能为空");
        }
        Example<CaseTestCaseDO> example = new Example<>();
        example.createCriteria().andEqualTo(CaseTestCaseDO::getUid, rootNode.getParentUid());
        List<CaseTestCaseDO> caseTestCaseDOS = testCaseMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(caseTestCaseDOS)) {
            throw new BusinessException("根节点不存在");
        }

        long projectId = caseTestCaseDOS.get(0).getProjectId();

        // 执行存储过程
        // 创建一个空的 map 来存储所有的 CaseTestCaseDO 实例
        Map<String, CaseTestCaseDO> nodeMap = new HashMap<>();

        // 计算层级关系
        computeHierarchy(rootNode, nodeMap, null, 0, projectId, 1);

        // 获取所有的 CaseTestCaseDO 实例
        List<CaseTestCaseDO> toBeSaved = new ArrayList<>(nodeMap.values());

        // 批量插入数据库
        int rowsAffected = testCaseEnhancedMapper.batchInsert(toBeSaved);
        log.info("插入了 {} 行.", rowsAffected);
        return rowsAffected == toBeSaved.size();
    }

    private Map<String, CaseTestCaseDO> computeHierarchy(FrontendTcNode rootNode, Map<String, CaseTestCaseDO> nodeMap,
                                                         String parentUid, int depth, long projectId, int sortOrder) {
        CaseTestCaseDO caseTestCaseDO = convertToDataObject(rootNode);
        if (null != parentUid) {
            caseTestCaseDO.setParentUid(parentUid);
        }
        caseTestCaseDO.setDepth(depth);
        caseTestCaseDO.setProjectId(projectId);
        caseTestCaseDO.setSortOrder(sortOrder);
        // 目前只有导入场景会走到这，会给每个节点生成uid
        if (StringUtils.isBlank(rootNode.getData().getUid())) {
            caseTestCaseDO.setUid(UUID.randomUUID().toString());
        } else {
            caseTestCaseDO.setUid(rootNode.getData().getUid());
        }
        nodeMap.put(caseTestCaseDO.getUid(), caseTestCaseDO);

        int orderForCurrentDepth = 1;
        if (rootNode.getChildren() != null && !rootNode.getChildren().isEmpty()) {
            for (FrontendTcNode childNode : rootNode.getChildren()) {
                computeHierarchy(childNode, nodeMap, caseTestCaseDO.getUid(), depth + 1, projectId, orderForCurrentDepth);
                orderForCurrentDepth++;
            }
        }
        return nodeMap;
    }

    @Transactional
    public boolean batchAddNode(ChangeAction changeAction) {
//        List<FrontendTcNode> nodes = changeAction.getNodeList();
        if (CollectionUtils.isEmpty(changeAction.getNodeList())) {
            return false;
        }

        // 循环插入子树
        Set<Boolean> saveResults = new HashSet<>();
        for (FrontendTcNode newSubTree : changeAction.getNodeList()) {
            saveResults.add(saveTree(newSubTree));
        }

        if (saveResults.size() == 2 || saveResults.contains(false)) {
            throw new BusinessException("批量插入子树失败");
        }

        // 更新父节点children的sorterOrder
        updateOrder(changeAction.getOrderData(),
                changeAction.getNodeList()
                .stream()
                .map(FrontendTcNode::getData)
                .map(NodeData::getUid)
                .collect(Collectors.toList()));
        return true;
    }

    public boolean batchDeleteNode(ChangeAction changeAction) {
        List<FrontendTcNode> nodes = changeAction.getNodeList();
        if (CollectionUtils.isEmpty(nodes)) {
            return true;
        }

        Set<String> uidList = nodes.stream().map(FrontendTcNode::getData).map(NodeData::getUid).collect(Collectors.toSet());

        Example<CaseTestCaseDO> example = new Example<>();
        Example.Criteria<CaseTestCaseDO> criteria = example.createCriteria();
        criteria.andIn(CaseTestCaseDO::getUid, uidList);

        List<CaseTestCaseDO> caseTestCaseDOS = testCaseMapper.selectByExample(example);

        if (caseTestCaseDOS.size() != uidList.size()) {
            throw new BusinessException("有不存在的节点，请刷新页面");
        }

        caseTestCaseDOS.forEach(caseTestCaseDO -> caseTestCaseDO.setIsDeleted(true));
//        testCaseMapper.up

        int updateResult = testCaseEnhancedMapper.batchUpdate(caseTestCaseDOS);
        return updateResult > 0;
    }

    /**
     * 数据转换，但不拷贝深度和children
     *
     * @param node
     * @return
     */
    private CaseTestCaseDO convertToDataObject(FrontendTcNode node) {
        CaseTestCaseDO caseTestCaseDO = new CaseTestCaseDO();
        caseTestCaseDO.setUid(node.getData().getUid());
        caseTestCaseDO.setName(node.getData().getText());
        caseTestCaseDO.setTags(JSON.toJSONString(node.getData().getTag()));

        JSONObject extension = new JSONObject();
        extension.put("generalization", node.getData().getGeneralization());
        extension.put("expand", node.getData().isExpand());
        extension.put("isActive", node.getData().isActive());

        // 处理图片
        if (StringUtils.isNotBlank(node.getData().getImage())) {
            extension.put("image", node.getData().getImage());

            if (null != node.getData().getImageSize()) {
                JSONObject imageSize = new JSONObject();
                imageSize.put("width", node.getData().getImageSize().getWidth());
                imageSize.put("height", node.getData().getImageSize().getHeight());
                imageSize.put("custom", node.getData().getImageSize().getCustom());
                extension.put("imageSize", imageSize);
            }
        }
        caseTestCaseDO.setExtension(extension.toJSONString());
        caseTestCaseDO.setParentUid(node.getParentUid());

        return caseTestCaseDO;
    }

    /**
     * 将 CaseTestCaseDO 对象转换为 FrontendTcNode 节点
     *
     * @param caseTestCase 测试用例对象
     * @return 转换后的树节点
     */
    private FrontendTcNode convertToNode(CaseTestCaseDO caseTestCase) {
        FrontendTcNode node = new FrontendTcNode();
        node.setId(caseTestCase.getId());               // 设置 id
        node.setUid(caseTestCase.getUid());             // 设置 uid
        node.setParentUid(caseTestCase.getParentUid()); // 设置 parentId
        node.setSortOrder(caseTestCase.getSortOrder()); // 设置排序值

        NodeData data = new NodeData();
        data.setText(caseTestCase.getName());
        data.setUid(caseTestCase.getUid());
//        data.setTag(stringIsNull(caseTestCase.getTags()) ? new ArrayList<>() : JSON.parseArray(caseTestCase.getTags(), String.class));
        data.setGeneralization(caseTestCase.getBizExtension() != null ? Arrays.asList(caseTestCase.getBizExtension().split(",")) : new ArrayList<>());
        data.setExpand(true);  // 默认展开，按需求设置

        node.setData(data);
        node.setChildren(new ArrayList<>());

        JSONObject extension = JSON.parseObject(caseTestCase.getExtension());
        if (null != extension && StringUtils.isNotBlank(extension.getString("image"))) {
            data.setImage(extension.getString("image"));
            data.setImageSize(new ImageSize());
            data.getImageSize().setWidth(extension.getJSONObject("imageSize").getInteger("width"));
            data.getImageSize().setHeight(extension.getJSONObject("imageSize").getInteger("height"));
            data.getImageSize().setCustom(extension.getJSONObject("imageSize").getBoolean("custom"));
        }


        if (stringIsNull(caseTestCase.getTags())) {
            data.setTag(new ArrayList<>());
        } else {
            // 处理Tag新老数据
            try {
                List<String> oldTagData = JSON.parseArray(caseTestCase.getTags(), String.class);
                // 转换成新数据
                List<StyledTag> newTagData = oldTagData.stream().map(tag -> {
                    StyledTag styledTag = new StyledTag();
                    styledTag.setText(tag);
                    JSONObject tagStyle = new JSONObject();
                    if (!TagConstants.systemTags.contains(tag)) {
                        tagStyle.put("fill", "blue");
                        styledTag.setStyle(tagStyle);
                        return styledTag;
                    }
                    if (List.of("P0", "P1", "P2", "P3").contains(tag)) {
                        tagStyle.put("fill", TagConstants.priorityTagColors.get(Integer.parseInt(tag.substring(1))));
                    } else {
                        switch (tag) {
                            case "冒烟":
                                tagStyle.put("fill", TagConstants.markColors.get(0));
                                break;
                            case "基线":
                                tagStyle.put("fill", TagConstants.markColors.get(1));
                                break;
                            case "自动化":
                                tagStyle.put("fill", TagConstants.markColors.get(2));
                                break;
                            case "回归":
                                tagStyle.put("fill", TagConstants.markColors.get(3));
                                break;
                            default:
                                break;
                        }
                    }
                    styledTag.setStyle(tagStyle);
                    return styledTag;
                }).collect(Collectors.toList());

                data.setTag(newTagData);
            } catch (Exception e) {
                List<StyledTag> newTagData = JSON.parseArray(caseTestCase.getTags(), StyledTag.class);
                data.setTag(newTagData);
            }
        }


        return node;
    }



    private boolean stringIsNull(String tags) {
        if (StringUtils.isEmpty(tags)) {
            return true;
        }
        if (tags.equals("null")) {
            return true;
        }
        if (tags.equals("[]")) {
            return true;
        }
        return false;
    }

    private void updateOrder(List<OrderData> nodeOrder, List<String> newNodeUidList) {
        // 更新新父节点的子节点排序
        for (OrderData orderData : nodeOrder) {
            Example<CaseTestCaseDO> orderExample = new Example<>();
            orderExample.createCriteria()
                    .andEqualTo(CaseTestCaseDO::getUid, orderData.getNodeUid())
                    .andEqualTo(CaseTestCaseDO::getIsDeleted, false);

            List<CaseTestCaseDO> childrenDataObjects = testCaseMapper.selectByExample(orderExample);
            if (CollectionUtils.isEmpty(childrenDataObjects)) {
                // 增加豁免机制，事务中，insert不会直接写到库中，故内存检查
                if (newNodeUidList.contains(orderData.getNodeUid())) {
                    continue;
                }
                throw new BusinessException("节点不存在，请刷新页面");
            }
            CaseTestCaseDO nodeDataObject = childrenDataObjects.get(0);
            nodeDataObject.setSortOrder(orderData.getNewSortOrder());
            testCaseMapper.updateByPrimaryKeySelective(nodeDataObject);
        }
    }

    private void sortChildren(FrontendTcNode node) {
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            node.getChildren().sort(Comparator.comparingInt(FrontendTcNode::getSortOrder));
            for (FrontendTcNode child : node.getChildren()) {
                sortChildren(child);
            }
        }
    }

}
