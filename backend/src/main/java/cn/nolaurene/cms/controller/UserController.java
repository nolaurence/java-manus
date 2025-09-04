package cn.nolaurene.cms.controller;

import cn.nolaurene.cms.common.dto.LoginRequest;
import cn.nolaurene.cms.common.dto.RegisterRequest;
import cn.nolaurene.cms.common.dto.UserSearchRequest;
import cn.nolaurene.cms.common.enums.ErrorCode;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.UserLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static cn.nolaurene.cms.common.constants.UserConstants.USER_LOGIN_STATE;

@RestController
@Tag(name = "用户API")
@RequestMapping("/user")
public class UserController {

    @Resource
    UserLoginService userLoginService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public BaseWebResult<Long> userRegister(@RequestBody RegisterRequest registerRequest) {
        // 校验
        if (null == registerRequest || StringUtils.isAnyBlank(
                registerRequest.getAccount(),
                registerRequest.getPassword(),
                registerRequest.getCheckPassword(),
                registerRequest.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR.getCode(), ErrorCode.PARAMS_ERROR.getMessage());
        }
        long userId = userLoginService.register(registerRequest);

        return BaseWebResult.success(userId);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public BaseWebResult<User> userLogin(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        return BaseWebResult.success(userLoginService.login(loginRequest.getAccount(), loginRequest.getPassword(), request));
    }

    @GetMapping("/logout")
    @Operation(summary = "用户登出")
    public BaseWebResult userLogout(HttpServletRequest request) {
        if (null == request) {
            throw new BusinessException(ErrorCode.NOT_LOGIN.getCode(), ErrorCode.NOT_LOGIN.getMessage());
        }
        int result = userLoginService.logout(request);
        return BaseWebResult.success(result);
    }

    /**
     * 获取当前用户
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前用户")
    public BaseWebResult<User> getCurrentUser(HttpServletRequest request) {
        User userFromDb = userLoginService.getCurrentUserInfo(request);

        if (ObjectUtils.isEmpty(userFromDb)) {
            return BaseWebResult.fail("获取失败：该用户不存在");
        }
        return BaseWebResult.success(userFromDb);
    }

    @PostMapping("/search/paged")
    @Operation(summary = "分页查询用户")
    public BaseWebResult<PagedData<User>> searchUserPaged(@RequestBody UserSearchRequest request) {
        return BaseWebResult.success(userLoginService.searchPagedUser(request));
    }
}
