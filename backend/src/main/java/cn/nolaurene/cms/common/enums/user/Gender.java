package cn.nolaurene.cms.common.enums.user;

import lombok.Getter;

@Getter
public enum Gender {
    MALE(1, "男"),
    FEMALE(2, "女");

    private final Integer code;

    private final String desc;

    Gender(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static Gender getByCode(int code) {
        // 生成一个根据code查找Gender的方法
        for (Gender gender : Gender.values()) {
            if (gender.code.equals(code)) {
                return gender;
            }
        }
        return Gender.MALE;
    }
}
