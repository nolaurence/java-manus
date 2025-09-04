package cn.nolaurene.cms.common.vo;

import lombok.Data;

/**
 * 和ant design pro 前端的CurrentUser保持一致
 */
@Data
public class User {

    /**
     * 登录用的账号，请求里一般是username
     */
    private String account;

    private String name;

    private String avatar;

    private String gender;

    private Long userid;

    private String email;

    private String signature;

    private String title;

    private String group;

    private Integer notifyCount;

    private Integer unreadCount;

    private String country;

    private String access;

    private String address;

    private String phone;

    private Integer status;

    private Integer role;
}
