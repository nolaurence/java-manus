package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.Data;

import java.util.Date;

/**
 * Description:项目信息
 * @author 郭富康
 * Date 2024-11-02
 */
@Data
@Table("projects")
public class ProjectsDO {

    /**
     * 主键
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * 项目名称
     */
    @Column("name")
    private String name;

    /**
     * 所属组织节点id
     */
    @Column("organization_id")
    private Long organizationId;

    /**
     * 创建者名字
     */
    @Column("creator_name")
    private String creatorName;

    /**
     * 测试用例根节点id
     */
    @Column("case_root_id")
    private Long caseRootId;

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
     * 是否删除，0未删除，1已删除
     */
    @Column("is_deleted")
    private Boolean isDeleted;

    /**
     * 测试用例根节点uid
     */
    @Column("case_root_uid")
    private String caseRootUid;
}