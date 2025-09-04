package cn.nolaurene.cms.controller;

import cn.nolaurene.cms.common.vo.BaseWebResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "用于应用维护的api")
@RequestMapping("/maintenance")
public class MaintenanceController {

    @Value("${maintenance.env}")
    private String envString;

    @GetMapping("/environment")
    @Operation(summary = "获取当前的运行环境")
    public BaseWebResult<String> getCurrentEnvironment() {
        return BaseWebResult.success(envString);
    }
}
