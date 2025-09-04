package cn.nolaurene.cms.dal.entity;


import io.mybatis.provider.Entity;
import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.Data;

import java.util.Date;

/**
 * Description:组织架构表
 * @author 郭富康
 * Date 2024-11-01
 */
@Data
@Table(value = "organization_structure")
public class OrganizationStructureDO {

    /**
     * id
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long orgId;

    /**
     * 名字
     */
    @Column("name")
    private String name;

    /**
     * 父节点id
     */
    @Column("parent_id")
    private Long parentId;

    /**
     * 组织架构描述
     */
    @Column("description")
    private String description;

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
     * 是否删除：0未删除，1已删除
     */
    @Column("is_deleted")
    private Boolean isDeleted;
}