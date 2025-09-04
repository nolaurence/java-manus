package cn.nolaurene.cms.common.enums.user;

import lombok.Getter;

@Getter
public enum UserRole {
    ADMIN(1, "管理员"),
    USER(2, "普通用户");

    private final int code;

    private final String desc;

    UserRole(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserRole getByCode(int code) {
        // 生成一个根据code查找Role的方法
        for (UserRole role : UserRole.values()) {
            if (role.code == code) {
                return role;
            }
        }
        // 默认为低权限的用户
        return UserRole.USER;
    }
}
