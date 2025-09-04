package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.Data;

import java.util.Date;

/**
 * Description:执行用例
 * @author 郭富康
 * Date 2024-12-13
 */
@Data
@Table("case_execute_case")
public class ExecuteCaseDO {

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
     * 用例标题
     */
    @Column("name")
    private String name;

    /**
     * 标签
     */
    @Column("tags")
    private String tags;

    /**
     * 深度
     */
    @Column("depth")
    private Integer depth;

    /**
     * 节点uuid
     */
    @Column("uid")
    private String uid;

    /**
     * 所属项目id
     */
    @Column("project_id")
    private Long projectId;

    /**
     * 所属测试计划id
     */
    @Column("plan_id")
    private Long planId;

    /**
     * 执行状态：0未执行，1跳过，2已执行
     */
    @Column("execute_status")
    private Integer executeStatus;

    /**
     * 扩展字段
     */
    @Column("extension")
    private String extension;
}
