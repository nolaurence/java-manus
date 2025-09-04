package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.Data;

import java.util.Date;

/**
 * Description:测试用例
 *
 * @author 郭富康
 * Date 2024-10-30
 */
@Data
@Table("case_test_case")
public class CaseTestCaseDO {

    /**
     * 主键
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * 父节点uid
     */
    @Column("parent_uid")
    private String parentUid;

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
     * 创建时间
     */
    @Column("gmt_create")
    private Date gmtCreate;

    /**
     * 修改时间
     */
    @Column("gmt_modified")
    private Date gmtModified;

    /**
     * 测试用例标题
     */
    @Column("name")
    private String name;

    /**
     * 业务扩展，存前置条件，执行步骤，预期结果的
     */
    @Column("biz_extension")
    private String bizExtension;

    /**
     * 扩展字段
     */
    @Column("extension")
    private String extension;

    /**
     * 标签
     */
    @Column("tags")
    private String tags;

    /**
     * 是否删除：0未删除，1已删除
     */
    @Column("is_deleted")
    private Boolean isDeleted;

    /**
     * 所属项目id
     */
    @Column("project_id")
    private Long projectId;

    /**
     * 节点顺序
     */
    @Column("sort_order")
    private Integer sortOrder;
}