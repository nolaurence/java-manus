package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;


import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author nolaurence
 * @date 2025/7/8 下午1:31
 * @description: 用于测试tool schema是否能正常工作的类
 */
@Data
public class UserDTO {

    @FieldDescription("用户的唯一标识符")
    private String id;

    @FieldDescription("用户标签")
    private List<String> tags;

    @FieldDescription("用户元数据信息")
    private Metadata metadata;

    @FieldDescription("多个地址信息")
    private List<Address> addresses;

    @FieldDescription("动态属性")
    private Map<String, String> attributes;

    // getters and setters...
}

@Data
class Metadata {
    @FieldDescription("用户偏好设置")
    private String preferences;

    @FieldDescription("用户各项评分")
    private Integer scores;

    // getters and setters...
}

@Data
class Address {
    @FieldDescription("街道名称")
    private String street;

    @FieldDescription("城市名称")
    private String city;

    // getters and setters...
}
