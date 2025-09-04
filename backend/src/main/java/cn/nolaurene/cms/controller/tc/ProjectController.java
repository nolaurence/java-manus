package cn.nolaurene.cms.controller.tc;

import cn.nolaurene.cms.common.dto.tc.ProjectCreateRequest;
import cn.nolaurene.cms.common.dto.tc.ProjectUpdateRequest;
import cn.nolaurene.cms.common.dto.tc.ProjectsRequest;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.common.vo.tc.Project;
import cn.nolaurene.cms.dal.entity.ProjectsDO;
import cn.nolaurene.cms.service.UserLoginService;
import cn.nolaurene.cms.service.tc.ProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/projects")
@Tag(name = "测试用例项目相关api")
public class ProjectController {

    @Resource
    private UserLoginService userLoginService;

    @Resource
    private ProjectService projectService;

    @PostMapping("/add")
    public BaseWebResult<Boolean> addProject(@RequestBody ProjectCreateRequest request, HttpServletRequest httpServletRequest) {
        return BaseWebResult.success(projectService.addProject(request, userLoginService.getCurrentUserInfo(httpServletRequest).getName()));
    }

    @PostMapping("/update")
    public BaseWebResult<Boolean> updateProject(@RequestBody ProjectUpdateRequest request, HttpServletRequest httpServletRequest) {
        return BaseWebResult.success(projectService.updateProject(request));
    }

    @PostMapping("/get")
    public BaseWebResult<PagedData<Project>> getProjects(@RequestBody ProjectsRequest request, HttpServletRequest httpServletRequest) {
        if (null != request.getOnlyMe() && request.getOnlyMe()) {
            request.setCreatorName(userLoginService.getCurrentUserInfo(httpServletRequest).getName());
        }
        PagedData<ProjectsDO> projectsDOList = projectService.getProjects(request);
        PagedData<Project> result = new PagedData<>();
        result.setPagination(projectsDOList.getPagination());
        result.setList(projectsDOList.getList().stream().map(this::convert).collect(Collectors.toList()));
        return BaseWebResult.success(result);
    }

    @GetMapping("/get/{id}")
    public BaseWebResult<Project> getProject(@PathVariable Long id) {
        return BaseWebResult.success(convert(projectService.getProject(id)));
    }

    private Project convert(ProjectsDO projectsDO) {
        Project project = new Project();
        project.setId(projectsDO.getId());
        project.setName(projectsDO.getName());
        project.setCreatorName(projectsDO.getCreatorName());
        project.setModifyTime(projectsDO.getGmtModified());
        project.setCaseRootId(projectsDO.getCaseRootId());
        project.setCaseRootUid(projectsDO.getCaseRootUid());
        return project;
    }

}
