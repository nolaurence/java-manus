package cn.nolaurene.cms.common.dto;

import lombok.Data;

/**
 * @author nolaurence
 * @description LLM配置响应对象
 */
@Data
public class LlmConfigResponse {
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
