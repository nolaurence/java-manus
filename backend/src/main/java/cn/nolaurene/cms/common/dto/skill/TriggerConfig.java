package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

/**
 * 触发器配置
 *
 * @author nolaurence
 */
@Data
public class TriggerConfig {

    /**
     * 触发类型：keyword, regex, intent
     */
    private String type;

    /**
     * 匹配模式
     */
    private String pattern;

    /**
     * 置信度（0-1）
     */
    private Double confidence;
}
