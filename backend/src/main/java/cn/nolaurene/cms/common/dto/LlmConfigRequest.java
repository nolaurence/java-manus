package cn.nolaurene.cms.common.dto;

import lombok.Data;

/**
 * @author nolaurence
 * @description LLM配置请求对象
 */
@Data
public class LlmConfigRequest {
    /**
     * LLM服务端点
     */
    private String endpoint;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String modelName;
}
