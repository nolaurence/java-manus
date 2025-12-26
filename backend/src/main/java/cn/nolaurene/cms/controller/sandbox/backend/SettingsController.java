package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.common.dto.LlmConfigRequest;
import cn.nolaurene.cms.common.dto.LlmConfigResponse;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.dal.entity.LlmConfigDO;
import cn.nolaurene.cms.service.LlmConfigService;
import cn.nolaurene.cms.service.UserLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author nolaurence
 * @description 设置控制器
 */
@Slf4j
@RestController
@Tag(name = "设置API")
@RequestMapping("/api/settings")
public class SettingsController {

    @Resource
    private LlmConfigService llmConfigService;

    @Resource
    private UserLoginService userLoginService;

    @GetMapping("/llm-config")
    @Operation(summary = "获取大模型配置")
    public BaseWebResult<LlmConfigResponse> getLlmConfig(HttpServletRequest request) {
        // 获取当前用户
        User currentUser = userLoginService.getCurrentUserInfo(request);
        if (currentUser == null) {
            return BaseWebResult.fail("未登录");
        }
        
        // 从数据库查询配置
        LlmConfigDO configDO = llmConfigService.getByUserId(currentUser.getUserid());
        
        LlmConfigResponse config = new LlmConfigResponse();
        if (configDO != null) {
            config.setEndpoint(configDO.getEndpoint());
            config.setApiKey(configDO.getApiKey());
            config.setModelName(configDO.getModelName());
        } else {
            // 返回空配置
            config.setEndpoint("");
            config.setApiKey("");
            config.setModelName("");
        }
        
        return BaseWebResult.success(config);
    }

    @PostMapping("/llm-config")
    @Operation(summary = "更新大模型配置")
    public BaseWebResult<Void> updateLlmConfig(@RequestBody LlmConfigRequest request, HttpServletRequest httpRequest) {
        // 参数校验
        if (request == null || StringUtils.isAnyBlank(request.getEndpoint(), request.getApiKey(), request.getModelName())) {
            return BaseWebResult.fail("配置参数不能为空");
        }

        // 获取当前用户
        User currentUser = userLoginService.getCurrentUserInfo(httpRequest);
        if (currentUser == null) {
            return BaseWebResult.fail("未登录");
        }

        // 保存到数据库
        try {
            llmConfigService.saveOrUpdate(
                currentUser.getUserid(),
                request.getEndpoint().trim(),
                request.getApiKey().trim(),
                request.getModelName().trim()
            );
            
            log.info("用户更新LLM配置: userId={}, endpoint={}, modelName={}", 
                    currentUser.getUserid(), request.getEndpoint(), request.getModelName());
            
            return BaseWebResult.success(null);
        } catch (Exception e) {
            log.error("保存LLM配置失败", e);
            return BaseWebResult.fail("保存配置失败: " + e.getMessage());
        }
    }
}
