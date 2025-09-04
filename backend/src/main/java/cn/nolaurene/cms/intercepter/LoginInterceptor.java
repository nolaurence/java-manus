package cn.nolaurene.cms.intercepter;

import cn.nolaurene.cms.common.enums.ErrorCode;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.UserLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

import static cn.nolaurene.cms.common.constants.UserConstants.USER_LOGIN_STATE;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Value("${maintenance.env}")
    private String env;

    @Resource
    private UserLoginService userLoginService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        User currentUser = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        if (currentUser == null && !List.of("local", "backend", "worker").contains(env)) {
            String requestURL = request.getRequestURL().toString();
            throw new BusinessException(ErrorCode.NOT_LOGIN.getCode(), "用户未登录，URL：" + requestURL);
        }
        return true;
    }
}
