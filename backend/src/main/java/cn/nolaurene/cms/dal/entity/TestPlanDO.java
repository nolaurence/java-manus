package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.*;

import java.util.Date;

/**
 * Description:测试计划表
 * @author 郭富康
 * Date 2024-12-13
 */
@Data
@Table("case_test_plan")
public class TestPlanDO {

    /**
     * 主键
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * 创建时间
     */
    @Column("gmt_create")
    private Date gmtCreate;

    /**
     * 更新时间
     */
    @Column("gmt_modified")
    private Date gmtModified;

    /**
     * 1：已删除 0：未删除
     */
    @Column("is_deleted")
    private Boolean isDeleted;

    /**
     * 测试计划名称
     */
    @Column("name")
    private String name;

    /**
     * 创建人姓名
     */
    @Column("creator")
    private String creator;

    /**
     * 测试进度
     */
    @Column("progress")
    private Integer progress;

    /**
     * 扩展字段
     */
    @Column("extension")
    private String extension;

    /**
     * 组织id
     */
    @Column("organization_id")
    private Long organizationId;
}