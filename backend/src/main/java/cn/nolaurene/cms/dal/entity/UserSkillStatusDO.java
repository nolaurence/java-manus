package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * 用户Skill状态实体类
 *
 * @author nolaurence
 */
@Data
@Table("user_skill_status")
public class UserSkillStatusDO {

    /**
     * 主键ID
     */
    @Column(id = true, remark = "主键ID", updatable = false, insertable = false)
    private Long id;

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * Skill ID
     */
    @Column("skill_id")
    private String skillId;

    /**
     * 状态：1-启用, 0-禁用
     */
    @Column("status")
    private Integer status;

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
