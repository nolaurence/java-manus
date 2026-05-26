package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * @author nolaurence
 * @description Agent技能关联实体类
 */
@Data
@Table("agent_skills")
public class AgentSkillDO {

    /**
     * 主键ID
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * Agent ID
     */
    @Column("agent_id")
    private String agentId;

    /**
     * 技能标识
     */
    @Column("skill_id")
    private String skillId;

    /**
     * 是否启用
     */
    @Column("enabled")
    private Boolean enabled;

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
