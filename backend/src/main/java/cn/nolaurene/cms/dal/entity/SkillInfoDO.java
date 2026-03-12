package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * Skill信息实体类
 * 遵循 https://agentskills.io/specification 规范
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
     * - 1-64 字符
     * - 只能包含小写字母、数字和连字符
     * - 不能以连字符开头或结尾
     * - 不能包含连续连字符
     */
    @Column("name")
    private String name;

    /**
     * Skill描述
     * - 1-1024 字符
     * - 描述技能功能和何时使用
     */
    @Column("description")
    private String description;

    /**
     * 版本号
     */
    @Column("version")
    private String version;

    /**
     * 许可证（可选）
     */
    @Column("license")
    private String license;

    /**
     * 兼容性说明（可选）
     * - 最多 500 字符
     * - 说明环境要求、系统包、网络访问等
     */
    @Column("compatibility")
    private String compatibility;

    /**
     * 元数据（可选）：任意键值对，JSON格式
     */
    @Column("metadata")
    private String metadata;

    /**
     * 允许使用的工具列表（可选，实验性）
     * - 空格分隔的工具列表
     * - 例如: "Bash(git:*) Bash(jq:*) Read"
     */
    @Column("allowed_tools")
    private String allowedTools;

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
