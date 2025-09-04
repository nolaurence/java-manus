package cn.nolaurene.cms.service.tc;

import cn.nolaurene.cms.common.dto.Pagination;
import cn.nolaurene.cms.common.dto.tc.TestPlanCreateRequest;
import cn.nolaurene.cms.common.dto.tc.TestPlanListRequest;
import cn.nolaurene.cms.common.dto.tc.TestPlanUpdateRequest;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.common.vo.tc.TestPlan;
import cn.nolaurene.cms.dal.entity.TestPlanDO;
import cn.nolaurene.cms.dal.mapper.TestPlanMapper;
import cn.nolaurene.cms.exception.BusinessException;
import io.mybatis.mapper.example.Example;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TestPlanService {

    @Resource
    TestPlanMapper testPlanMapper;

    public boolean createTestPlan(TestPlanCreateRequest request) {
        TestPlanDO testPlanDO = new TestPlanDO();
        testPlanDO.setName(request.getPlanName());
        testPlanDO.setOrganizationId(request.getOrganizationId());
        testPlanDO.setCreator(request.getCreatorName());
        testPlanDO.setProgress(0);
        int i = testPlanMapper.insertSelective(testPlanDO);

        return i > 0;
    }

    public boolean updateTestPlan(TestPlanUpdateRequest request) {
        TestPlanDO testPlanDO = new TestPlanDO();
        testPlanDO.setId(request.getId());
        testPlanDO.setName(request.getPlanName());
        testPlanDO.setOrganizationId(request.getOrganizationId());
        testPlanDO.setCreator(request.getCreatorName());
        int i = testPlanMapper.updateByPrimaryKeySelective(testPlanDO);

        return i > 0;
    }

    public boolean deleteTestPlan(Long id) {
        Optional<TestPlanDO> testPlanDOOptional = testPlanMapper.selectByPrimaryKey(id);

        if (testPlanDOOptional.isEmpty()) {
            throw new BusinessException("测试计划不存在");
        }
        TestPlanDO testPlanDO = testPlanDOOptional.get();
        if (testPlanDO.getIsDeleted()) {
            return true;
        }
        testPlanDO.setIsDeleted(true);
        int i = testPlanMapper.updateByPrimaryKeySelective(testPlanDO);

        return i > 0;
    }

    public PagedData<TestPlan> listTestPlan(TestPlanListRequest request) {
        Example<TestPlanDO> example = new Example<>();
        Example.Criteria<TestPlanDO> criteria = example.createCriteria();

        if (null == request.getOrganizationId()) {
            throw new BusinessException("组织id不能为空");
        }
        criteria.andEqualTo(TestPlanDO::getOrganizationId, request.getOrganizationId());

        if (StringUtils.isNotBlank(request.getPlanName())) {
            criteria.andLike(TestPlanDO::getName, "%" + request.getPlanName() + "%");
        }
        if (StringUtils.isNotBlank(request.getCreatorName())) {
            criteria.andLike(TestPlanDO::getName, "%" + request.getCreatorName() + "%");
        }
        criteria.andEqualTo(TestPlanDO::getIsDeleted, false);
        example.orderBy(TestPlanDO::getGmtCreate, Example.Order.DESC);

        RowBounds rowBounds = new RowBounds((request.getCurrent() - 1) * request.getPageSize(), request.getPageSize());
        List<TestPlanDO> testPlanDOList = testPlanMapper.selectByExample(example, rowBounds);
        long count = testPlanMapper.countByExample(example);

        PagedData<TestPlan> pagedData = new PagedData<>();
        pagedData.setList(testPlanDOList.stream().map(this::fromDataObject).collect(Collectors.toList()));
        pagedData.setPagination(Pagination.of(request.getCurrent(), request.getPageSize(), count));
        return pagedData;
    }

    private TestPlan fromDataObject(TestPlanDO testPlanDO) {
        TestPlan testPlan = new TestPlan();
        testPlan.setId(testPlanDO.getId());
        testPlan.setPlanName(testPlanDO.getName());
        testPlan.setCreatorName(testPlanDO.getCreator());
        testPlan.setTestProgress(testPlanDO.getProgress());
        testPlan.setGmtCreate(testPlanDO.getGmtCreate());
        testPlan.setGmtModified(testPlanDO.getGmtModified());
        return testPlan;
    }
}
