package cn.nolaurene.cms.controller.tc;

import cn.nolaurene.cms.common.dto.tc.TagRequest;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.common.vo.tc.TagVO;
import cn.nolaurene.cms.service.UserLoginService;
import cn.nolaurene.cms.service.tc.TagService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/tags")
@Tag(name = "测试用例标签相关api")
public class TagController {

    @Resource
    private TagService tagService;

    @Resource
    private UserLoginService userLoginService;

    @GetMapping("/add")
    public BaseWebResult<Boolean> addTag(@RequestParam String tagName, HttpServletRequest httpServletRequest) {
        return BaseWebResult.success(tagService.addTag(tagName, userLoginService.getCurrentUserInfo(httpServletRequest).getName()));
    }

    @GetMapping("/delete")
    public BaseWebResult<Boolean> deleteTag(@RequestParam long tagId) {
        return BaseWebResult.success(tagService.deleteTag(tagId));
    }

    @PostMapping("/search")
    public BaseWebResult<PagedData<TagVO>> searchPagedTagList(@RequestBody TagRequest request) {
        int currentPage = request.getCurrent() == 0 ? 1 : request.getCurrent();
        int pageSize = request.getPageSize() == 0 ? 10 : request.getPageSize();
        request.setCurrent(currentPage);
        request.setPageSize(pageSize);
        return BaseWebResult.success(tagService.getTagList(request));
    }

}
