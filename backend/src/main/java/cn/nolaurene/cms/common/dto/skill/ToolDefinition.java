package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.Map;

/**
 * 工具定义
 *
 * @author nolaurence
 */
@Data
public class ToolDefinition {

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 参数定义
     */
    private Map<String, Object> parameters;

    /**
     * 执行器类型：shell, http, function
     */
    private String executor;

    /**
     * Shell命令模板
     */
    private String command;

    /**
     * HTTP端点
     */
    private String endpoint;

    /**
     * HTTP方法
     */
    private String method;

    /**
     * 超时时间（毫秒）
     */
    private Long timeout;
}
