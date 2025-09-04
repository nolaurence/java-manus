package cn.nolaurene.cms.controller.tc;

import cn.nolaurene.cms.service.tc.TestPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author guofukang.gfk
 * @date 2024/12/23.
 */
@RestController
@RequestMapping("/testPlan")
@Tag(name = "测试计划标签相关api")
public class TestPlanController {

    @Resource
    private TestPlanService testPlanService;
}
