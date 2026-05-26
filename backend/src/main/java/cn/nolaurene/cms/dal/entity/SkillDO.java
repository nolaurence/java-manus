package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * @author nolaurence
 * @description 技能实体类
 */
@Data
@Table("skills")
public class SkillDO {

    /**
     * 主键ID
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * 技能唯一标识
     */
    @Column("skill_id")
    private String skillId;

    /**
     * 用户ID
     */
    @Column("user_id")
    private String userId;

    /**
     * 技能名称
     */
    @Column("name")
    private String name;

    /**
     * 技能描述
     */
    @Column("description")
    private String description;

    /**
     * 版本号
     */
    @Column("version")
    private String version;

    /**
     * 标签，逗号分隔
     */
    @Column("tags")
    private String tags;

    /**
     * 是否启用
     */
    @Column("enabled")
    private Boolean enabled;

    /**
     * 技能源码目录
     */
    @Column("source_dir")
    private String sourceDir;

    /**
     * 安装时间
     */
    @Column("installed_at")
    private Date installedAt;

    /**
     * 更新时间
     */
    @Column("updated_at")
    private Date updatedAt;
}
