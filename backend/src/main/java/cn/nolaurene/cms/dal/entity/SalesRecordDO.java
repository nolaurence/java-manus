package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Description:销售记录表
 * @author 郭富康
 * Date 2024-02-09
 */
@Data
@Table("sales_record")
public class SalesRecordDO {

    /**
     * 主键
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * 描述
     */
    @Column("description")
    private String description;

    /**
     * 交易金额
     */
    @Column("amount")
    private BigDecimal amount;

    /**
     * 图片url
     */
    @Column("image")
    private String image;

    /**
     * 客户id
     */
    @Column("client_id")
    private Long clientId;

    /**
     * 记录创建人
     */
    @Column("creator")
    private String creator;

    /**
     * 记录创建人名称
     */
    @Column("creator_name")
    private String creatorName;

    /**
     * 更新人
     */
    @Column("modifier")
    private String modifier;

    /**
     * 更新人名称
     */
    @Column("modifier_name")
    private String modifierName;

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
     * 是否删除：0否，1是
     */
    @Column("is_deleted")
    private Boolean isDeleted;
}