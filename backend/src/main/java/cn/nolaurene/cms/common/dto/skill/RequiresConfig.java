package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 依赖配置
 *
 * @author nolaurence
 */
@Data
public class RequiresConfig {

    /**
     * 依赖的二进制文件
     */
    private List<String> bins;

    /**
     * 环境变量
     */
    private Map<String, String> env;

    /**
     * 其他配置
     */
    private Map<String, Object> config;
}
