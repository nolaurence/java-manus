package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * Skill信息实体类
 *
 * @author nolaurence
 */
@Data
@Table("skill_info")
public class SkillInfoDO {

    /**
     * 主键ID
     */
    @Column(id = true, remark = "主键ID", updatable = false, insertable = false)
    private Long id;

    /**
     * Skill唯一标识符（如：author/skill-name）
     */
    @Column("skill_id")
    private String skillId;

    /**
     * Skill名称
     */
    @Column("name")
    private String name;

    /**
     * 版本号
     */
    @Column("version")
    private String version;

    /**
     * 作者
     */
    @Column("author")
    private String author;

    /**
     * Skill描述
     */
    @Column("description")
    private String description;

    /**
     * 分类标签
     */
    @Column("category")
    private String category;

    /**
     * 触发器配置（JSON数组）
     */
    @Column("triggers")
    private String triggers;

    /**
     * 工具定义（JSON数组）
     */
    @Column("tools")
    private String tools;

    /**
     * 依赖配置（bins, env, config）
     */
    @Column("requires")
    private String requires;

    /**
     * 支持的操作系统列表
     */
    @Column("os_support")
    private String osSupport;

    /**
     * 优先级（Workspace > Local > Bundled）
     */
    @Column("priority")
    private Integer priority;

    /**
     * 状态：1-启用, 0-禁用
     */
    @Column("status")
    private Integer status;

    /**
     * 所属用户ID（NULL表示系统级）
     */
    @Column("user_id")
    private Long userId;

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
