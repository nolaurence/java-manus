package cn.nolaurene.cms.common.vo;

import cn.nolaurene.cms.common.enums.ErrorShowType;
import lombok.Data;

@Data
public class BaseWebResult<T> {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 数据
     */
    private T data;

    /**
     * 状态码
     */
    private String errorCode;

    /**
     * 返回的消息
     */
    private String errorMessage;

    /**
     * 错误展示的方法
     */
    private ErrorShowType errorShowType;

    public BaseWebResult(Boolean success, T data, String code, String message, ErrorShowType errorShowType) {
        this.success = success;
        this.data = data;
        this.errorCode = code;
        this.errorMessage = message;
        this.errorShowType = errorShowType;
    }

    public static <T> BaseWebResult<T> success(T data) {
        return new BaseWebResult<>(true, data, "", "success", null);
    }

    public static <T> BaseWebResult<T> fail(T data, String code, String message) {
        return new BaseWebResult<>(false, data, code, message, null);
    }

    public static <T> BaseWebResult<T> fail(String code, String message) {
        return new BaseWebResult<>(false, null, code, message, null);
    }

    private static <T> BaseWebResult<T> fail(String code, String message, ErrorShowType errorShowType) {
        return new BaseWebResult<>(false, null, code, message, errorShowType);
    }

    public static <T> BaseWebResult<T> fail(String message) {
        return new BaseWebResult<>(false, null, "", message, null);
    }

    public static <T> BaseWebResult<T> fail(String message, ErrorShowType errorShowType) {
        return new BaseWebResult<>(false, null, "", message, errorShowType);
    }
}
