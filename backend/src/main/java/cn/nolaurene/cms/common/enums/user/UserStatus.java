package cn.nolaurene.cms.common.enums.user;

import lombok.Getter;

@Getter
public enum UserStatus {
    NORMAL(0, "正常"),
    LOCKED(1, "锁定"),
    BANNED(2, "封禁");

    private final int code;

    private final String desc;

    UserStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserStatus getByCode(int code) {
        // 生成一个根据code查找UserStatus的方法
        for (UserStatus status : UserStatus.values()) {
            if (status.code == code) {
                return status;
            }
        }
        // 默认为正常状态
        return UserStatus.NORMAL;
    }
}
