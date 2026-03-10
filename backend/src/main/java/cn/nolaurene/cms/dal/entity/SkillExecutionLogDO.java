package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * Skill执行日志实体类
 *
 * @author nolaurence
 */
@Data
@Table("skill_execution_log")
public class SkillExecutionLogDO {

    /**
     * 主键ID
     */
    @Column(id = true, remark = "主键ID", updatable = false, insertable = false)
    private Long id;

    /**
     * 执行的Skill ID
     */
    @Column("skill_id")
    private String skillId;

    /**
     * 关联的会话ID
     */
    @Column("session_id")
    private String sessionId;

    /**
     * 执行用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 输入参数
     */
    @Column("input_params")
    private String inputParams;

    /**
     * 执行结果
     */
    @Column("output_result")
    private String outputResult;

    /**
     * 执行状态：SUCCESS, FAILED, TIMEOUT
     */
    @Column("status")
    private String status;

    /**
     * 错误信息
     */
    @Column("error_message")
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    @Column("duration_ms")
    private Long durationMs;

    /**
     * 创建时间
     */
    @Column("gmt_create")
    private Date gmtCreate;
}
