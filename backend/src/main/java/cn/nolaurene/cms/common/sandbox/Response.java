package cn.nolaurene.cms.common.sandbox;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: Generic response model for API interface return results
 */
@Data
public class Response<T> {

    private boolean success;

    private String message;

    private T data;

    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> Response<T> error(String message, T data) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}
