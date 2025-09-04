package cn.nolaurene.cms.service.sandbox.backend.tool;


import java.util.Map;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public interface Tool {

    String name();

    String description();

    String run(String input, Map<String, Object> context);
}
