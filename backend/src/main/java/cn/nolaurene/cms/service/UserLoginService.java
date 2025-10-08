package cn.nolaurene.cms.service;

import cn.nolaurene.cms.common.constants.UserConstants;
import cn.nolaurene.cms.common.dto.LoginRequest;
import cn.nolaurene.cms.common.dto.Pagination;
import cn.nolaurene.cms.common.dto.RegisterRequest;
import cn.nolaurene.cms.common.dto.UserSearchRequest;
import cn.nolaurene.cms.common.enums.ErrorCode;
import cn.nolaurene.cms.common.enums.user.Gender;
import cn.nolaurene.cms.common.enums.user.UserRole;
import cn.nolaurene.cms.common.enums.LoginErrorEnum;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.dal.entity.UserDO;
import cn.nolaurene.cms.dal.mapper.UserMapper;
import cn.nolaurene.cms.exception.BusinessException;
import io.mybatis.mapper.example.Example;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.NumberUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.nolaurene.cms.common.constants.UserConstants.USER_LOGIN_STATE;

@Service
@Slf4j
public class UserLoginService {

    private static final String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "yupi";

    @Resource
    UserMapper userMapper;

    public long register(RegisterRequest request) {
        // 1. 参数校验
        if (StringUtils.isAnyBlank(request.getAccount(), request.getPassword(), request.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR.getCode(), "参数为空");
        }
        if (request.getAccount().length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR.getCode(), "账号过段");
        }
        if (request.getPassword().length() < 8 || request.getCheckPassword().length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR.getCode(), "用户密码过短");
        }

        // 不能包含特殊字符
        Matcher matcher = Pattern.compile(validPattern).matcher(request.getAccount());
        if (matcher.find()) {
            return -1;
        }

        // 密码和校验密码相同
        if (!request.getPassword().equals(request.getCheckPassword())) {
            return -1;
        }

        // 账户不能重复
        UserDO userDO = new UserDO();
        userDO.setUserAccount(request.getAccount());
        Optional<UserDO> userDO1 = userMapper.selectOne(userDO);
        if (userDO1.isPresent()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR.getCode(), "行号重复");
        }

        // 2. 加密
        String encryptedPassword = DigestUtils.md5DigestAsHex((SALT + request.getPassword()).getBytes());

        // 3. 写数据库
        userDO.setUserAccount(request.getAccount());
        userDO.setUserPassword(encryptedPassword);
        userDO.setUserName(request.getName());
        userDO.setGender(request.getGender());
        userDO.setPhone(request.getPhone());
        userDO.setEmail(request.getEmail());
        userDO.setUserRole(UserRole.USER.getCode());

        int i = userMapper.insertSelective(userDO);
        if (i <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "注册失败");
        }
        return userDO.getId();
    }

    public User login(String userAccount, String password, HttpServletRequest httpServletRequest) {
        // 如果是https://localhost:8000请求，直接返回空对象
        if (httpServletRequest.getRequestURI().contains("http://localhost:8000")) {
            return getMockUserInfo();
        }
        // 1. 校验参数
        LoginErrorEnum checkResult = checkAccount(userAccount);
        if(checkResult != LoginErrorEnum.SUCCESS) {
            throw new BusinessException(checkResult.getErrorCode(), checkResult.getErrorMessage());
        }
        if (password.length() < UserConstants.MIN_PASSWORD_LENGTH) {
            throw new BusinessException(LoginErrorEnum.PASSWORD_TOO_SHORT.getErrorCode(), LoginErrorEnum.PASSWORD_TOO_SHORT.getErrorMessage());
        }

        // 2. 加密密码
        String encryptedPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());

        // 查询用户是否存在
        UserDO userDO = new UserDO();
        userDO.setUserAccount(userAccount);
        userDO.setUserPassword(encryptedPassword);
        userDO.setIsDelete(false);

        Optional<UserDO> userDO1 = userMapper.selectOne(userDO);
        if (userDO1.isEmpty()) {
            throw new BusinessException(LoginErrorEnum.PASSWORD_ERROR.getErrorCode(), LoginErrorEnum.PASSWORD_ERROR.getErrorMessage());
        }

        // 3. 用户脱敏：模型转换
        User userToReturn = getSafetyUser(userDO1.get());

        // 种cookie
        httpServletRequest.getSession().setAttribute(UserConstants.USER_LOGIN_STATE, userToReturn);
        return userToReturn;
    }

    public int logout(HttpServletRequest httpServletRequest) {
        // 移除登录态
        httpServletRequest.getSession().removeAttribute(UserConstants.USER_LOGIN_STATE);
        return 0;
    }

    public User getById(Long id) {

        // 查询用户是否存在
        UserDO userDO = new UserDO();
        userDO.setId(id);
        userDO.setIsDelete(false);

        Optional<UserDO> userDO1 = userMapper.selectOne(userDO);
        if (userDO1.isEmpty()) {
            throw new BusinessException(LoginErrorEnum.USER_NOT_EXIST.getErrorCode(), LoginErrorEnum.USER_NOT_EXIST.getErrorMessage());
        }

        // 用户脱敏：模型转换
        return getSafetyUser(userDO1.get());
    }

    public PagedData<User> searchPagedUser(UserSearchRequest request) {
        // 查询条件
        Example<UserDO> example = new Example<>();
        Example.Criteria<UserDO> criteria = example.createCriteria();
        if (StringUtils.isNoneBlank(request.getAccount())) {
            criteria.andLike(UserDO::getUserAccount, "%" + request.getAccount() + "%");
        }
        if (StringUtils.isNotBlank(request.getName())) {
            criteria.andLike(UserDO::getUserName, "%" + request.getName() + "%");
        }
        if (ObjectUtils.isNotEmpty(request.getUserid())) {
            criteria.andEqualTo(UserDO::getId, request.getUserid());
        }
        criteria.andEqualTo(UserDO::getIsDelete, false);
        example.orderBy(UserDO::getGmtModified, Example.Order.DESC);

        // 执行
        int offset = (request.getCurrent() - 1) * request.getPageSize();
        new RowBounds(offset, request.getPageSize());
        List<UserDO> userDOList = userMapper.selectByExample(example);
        long count = userMapper.countByExample(example);

        // 模型转换 + 分页参数准备
        List<User> userList = userDOList.stream().map(this::getSafetyUser).collect(java.util.stream.Collectors.toList());
        Pagination pagination = new Pagination();
        pagination.setCurrent(request.getCurrent());
        pagination.setPageSize(request.getPageSize());
        pagination.setTotal(count);
        PagedData<User> userPagedData = new PagedData<>();
        userPagedData.setPagination(pagination);
        userPagedData.setList(userList);

        return userPagedData;
    }

    public User getCurrentUserInfo(HttpServletRequest httpServletRequest) {
        if (httpServletRequest.getRequestURI().contains("http://localhost:8000")) {
            return getMockUserInfo();
        }
        User currentUser = (User) httpServletRequest.getSession().getAttribute(USER_LOGIN_STATE);

        if (null == currentUser) {
            throw new BusinessException(ErrorCode.NOT_LOGIN.getCode(), ErrorCode.NOT_LOGIN.getMessage());
        }
        return getById(currentUser.getUserid());
    }

    private LoginErrorEnum checkAccount(String userAccount) {
        if (StringUtils.isBlank(userAccount)) {
            return LoginErrorEnum.ACCOUNT_EMPTY;
        }
        if (userAccount.length() < UserConstants.MIN_USER_ACCOUNT_LENGTH) {
            return LoginErrorEnum.ACCOUNT_TOO_SHORT;
        }
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return LoginErrorEnum.ACCOUNT_BAD_CHARACTER;
        }
        return LoginErrorEnum.SUCCESS;
    }

    private User getSafetyUser(UserDO userDO) {
        User user = new User();
        user.setName(userDO.getUserName());
        user.setAvatar(userDO.getAvatarUrl());
        user.setPhone(userDO.getPhone());
        user.setEmail(userDO.getEmail());
        user.setAccount(userDO.getUserAccount());
        user.setRole(userDO.getUserRole());
        user.setUserid(userDO.getId());

        if (null != userDO.getUserRole()) {
            switch (userDO.getUserRole()) {
                case 1:
                    user.setAccess("canAdmin");
                    break;
                case 2:
                    user.setAccess("user");
                    break;
                default:
                    user.setAccess("user");
                    break;
            }
        }

        if (null != userDO.getGender()) {
            switch (Gender.getByCode(userDO.getGender())) {
                case MALE:
                    user.setGender(Gender.MALE.getDesc());
                    break;
                case FEMALE:
                    user.setGender(Gender.FEMALE.getDesc());
                    break;
                default:
                    break;
            }
        }
        return user;
    }

    private User getMockUserInfo() {
        User user = new User();
        user.setAccount("admin");
        user.setName("nice");
        user.setUserid(1L);
        user.setRole(1);
        user.setAccess("canAdmin");
        return user;
    }
}
