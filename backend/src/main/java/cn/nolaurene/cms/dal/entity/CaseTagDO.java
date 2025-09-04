package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.Data;

import java.util.Date;

/**
 * Description:用例标签表
 * @author 郭富康
 * Date 2024-11-19
 */
@Data
@Table("case_tag")
public class CaseTagDO {

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
     * 标签名字
     */
    @Column("tag")
    private String tag;

    /**
     * 创建人名称
     */
    @Column("creator_name")
    private String creatorName;

    /**
     * 删除时间，有则删除
     */
    @Column("gmt_deleted")
    private Date gmtDeleted;
}