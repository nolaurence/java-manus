package cn.nolaurene.cms.common.sandbox.backend.model;


import lombok.Data;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
@Data
public class SSEEvent<T> {

    private String event;

    private T data;
}
