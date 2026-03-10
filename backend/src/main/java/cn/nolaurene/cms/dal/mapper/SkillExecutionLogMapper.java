package cn.nolaurene.cms.dal.mapper;

import cn.nolaurene.cms.dal.entity.SkillExecutionLogDO;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Skill执行日志Mapper
 *
 * @author nolaurence
 */
public interface SkillExecutionLogMapper extends Mapper<SkillExecutionLogDO, Long> {

    /**
     * 根据skillId查询执行日志
     */
    @Select("SELECT * FROM skill_execution_log WHERE skill_id = #{skillId} ORDER BY gmt_create DESC LIMIT #{limit}")
    List<SkillExecutionLogDO> selectBySkillId(@Param("skillId") String skillId, @Param("limit") int limit);

    /**
     * 根据sessionId查询执行日志
     */
    @Select("SELECT * FROM skill_execution_log WHERE session_id = #{sessionId} ORDER BY gmt_create DESC")
    List<SkillExecutionLogDO> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据userId查询执行日志
     */
    @Select("SELECT * FROM skill_execution_log WHERE user_id = #{userId} ORDER BY gmt_create DESC LIMIT #{limit}")
    List<SkillExecutionLogDO> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
