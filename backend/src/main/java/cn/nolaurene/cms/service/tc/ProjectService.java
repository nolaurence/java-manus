package cn.nolaurene.cms.service.tc;

import cn.nolaurene.cms.common.dto.Pagination;
import cn.nolaurene.cms.common.dto.tc.ProjectCreateRequest;
import cn.nolaurene.cms.common.dto.tc.ProjectUpdateRequest;
import cn.nolaurene.cms.common.dto.tc.ProjectsRequest;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.dal.entity.CaseTestCaseDO;
import cn.nolaurene.cms.dal.entity.ProjectsDO;
import cn.nolaurene.cms.dal.mapper.CaseTestCaseMapper;
import cn.nolaurene.cms.dal.mapper.ProjectsMapper;
import cn.nolaurene.cms.exception.BusinessException;
import io.mybatis.mapper.example.Example;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectService {

    @Resource
    private ProjectsMapper projectsMapper;

    @Resource
    private CaseTestCaseMapper testCaseMapper;

    @Transactional
    public Boolean addProject(ProjectCreateRequest request, String creatorName) {
        ProjectsDO projectsDO = new ProjectsDO();
        projectsDO.setName(request.getName());
        projectsDO.setOrganizationId(request.getOrganizationId());
        projectsDO.setCreatorName(creatorName);
        int i = projectsMapper.insertSelective(projectsDO);

        // 默认创建测试用例
        if (i <= 0) {
            throw new BusinessException("添加项目失败");
        }
        CaseTestCaseDO caseDO = new CaseTestCaseDO();
        caseDO.setDepth(0);
        caseDO.setUid(UUID.randomUUID().toString());
        caseDO.setName(request.getName());
        caseDO.setProjectId(projectsDO.getId());
        int caseResult = testCaseMapper.insertSelective(caseDO);

        if (caseResult <= 0) {
            throw new BusinessException("添加测试用例失败");
        }

        projectsDO.setCaseRootUid(caseDO.getUid());
        int updateResult = projectsMapper.updateByPrimaryKeySelective(projectsDO);

        if (updateResult <= 0) {
            throw new BusinessException("更新项目失败");
        }

        return true;
    }

    public boolean updateProject(ProjectUpdateRequest request) {
        Optional<ProjectsDO> projectsDO = projectsMapper.selectByPrimaryKey(request.getId());
        if (!projectsDO.isPresent()) {
            throw new BusinessException("项目不存在");
        }
        ProjectsDO project = projectsDO.get();
        project.setName(request.getName());

        int i = projectsMapper.updateByPrimaryKeySelective(project);
        return i > 0;
    }

    public PagedData<ProjectsDO> getProjects(ProjectsRequest request) {
        Example<ProjectsDO> example = new Example<>();
        Example.Criteria<ProjectsDO> criteria = example.createCriteria();

        if (null != request.getOrganizationId()) {
            criteria.andEqualTo(ProjectsDO::getOrganizationId, request.getOrganizationId());
        }
        if (StringUtils.isNotBlank(request.getName())) {
            criteria.andLike(ProjectsDO::getName, "%" + request.getName() + "%");
        }
        if (StringUtils.isNotBlank(request.getCreatorName())) {
            criteria.andEqualTo(ProjectsDO::getCreatorName, request.getCreatorName());
        }
        criteria.andEqualTo(ProjectsDO::getIsDeleted, false);
        example.orderBy(ProjectsDO::getGmtModified, Example.Order.DESC);

        RowBounds rowBounds = new RowBounds((request.getCurrent() - 1) * request.getPageSize(), request.getPageSize());
        List<ProjectsDO> projectsDOList = projectsMapper.selectByExample(example, rowBounds);
        long count = projectsMapper.countByExample(example);

        PagedData<ProjectsDO> pagedData = new PagedData<>();
        Pagination pagination = new Pagination();
        pagination.setCurrent(request.getCurrent());
        pagination.setPageSize(request.getPageSize());
        pagination.setTotal(count);
        pagedData.setList(projectsDOList);
        pagedData.setPagination(pagination);
        return pagedData;
    }

    public ProjectsDO getProject(Long id) {
        return projectsMapper.selectByPrimaryKey(id).orElse(null);
    }
}
