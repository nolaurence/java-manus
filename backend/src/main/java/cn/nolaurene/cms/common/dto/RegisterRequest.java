package cn.nolaurene.cms.common.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    /**
     * 用户账号
     */
    private String account;

    /**
     * name
     */
    private String name;

    /**
     * 密码
     */
    private String password;

    /**
     * 校验密码
     */
    private String checkPassword;

    private Integer gender;

    private String email;

    private String phone;
}
