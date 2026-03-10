package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * Skill文档实体类
 *
 * @author nolaurence
 */
@Data
@Table("skill_document")
public class SkillDocumentDO {

    /**
     * 主键ID
     */
    @Column(id = true, remark = "主键ID", updatable = false, insertable = false)
    private Long id;

    /**
     * 关联的Skill ID
     */
    @Column("skill_id")
    private String skillId;

    /**
     * 文档类型：SKILL_MD, README, REFERENCE, EXAMPLE, SCRIPT
     */
    @Column("doc_type")
    private String docType;

    /**
     * 文档名称
     */
    @Column("doc_name")
    private String docName;

    /**
     * 文档内容
     */
    @Column("content")
    private String content;

    /**
     * 原始文件路径（可选）
     */
    @Column("file_path")
    private String filePath;

    /**
     * 排序顺序
     */
    @Column("sort_order")
    private Integer sortOrder;

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
     * 是否删除
     */
    @Column("is_delete")
    private Boolean isDelete;
}
