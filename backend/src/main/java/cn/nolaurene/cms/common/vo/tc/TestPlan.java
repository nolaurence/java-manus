package cn.nolaurene.cms.common.vo.tc;

import lombok.Data;

import java.util.Date;

@Data
public class TestPlan {

    /**
     * 主键
     */
    private Long id;

    /**
     * 测试计划名称
     */
    private String planName;

    /**
     * 创建人
     */
    private String creatorName;

    /**
     * 项目名称
     */
    private Integer testProgress;

    /**
     * 创建时间
     */
    private Date gmtCreate;

    /**
     * 修改时间
     */
    private Date gmtModified;
}
