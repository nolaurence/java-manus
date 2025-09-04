package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.Data;

import java.util.Date;

/**
 * Description:客户信息表
 * @author 郭富康
 * Date 2024-03-02
 */
@Data
@Table("clients")
public class ClientsDO {

    /**
     * 主键
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * 客户名称
     */
    @Column("name")
    private String name;

    /**
     * 创建人账号
     */
    @Column("creator_account")
    private String creatorAccount;

    /**
     * 更新人账号
     */
    @Column("modifier_account")
    private String modifierAccount;

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
}