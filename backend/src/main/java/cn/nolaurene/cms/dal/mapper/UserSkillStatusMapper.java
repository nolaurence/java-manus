package cn.nolaurene.cms.dal.mapper;

import cn.nolaurene.cms.dal.entity.UserSkillStatusDO;
import io.mybatis.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户Skill状态Mapper
 *
 * @author nolaurence
 */
@Mapper
public interface UserSkillStatusMapper extends BaseMapper<UserSkillStatusDO, Long> {

    /**
     * 根据用户ID和Skill ID查询状态
     */
    @Select("SELECT * FROM user_skill_status WHERE user_id = #{userId} AND skill_id = #{skillId} AND is_delete = 0")
    UserSkillStatusDO selectByUserIdAndSkillId(@Param("userId") Long userId, @Param("skillId") String skillId);

    /**
     * 查询用户启用的所有Skill ID
     */
    @Select("SELECT skill_id FROM user_skill_status WHERE user_id = #{userId} AND status = 1 AND is_delete = 0")
    List<String> selectEnabledSkillIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的所有Skill状态
     */
    @Select("SELECT * FROM user_skill_status WHERE user_id = #{userId} AND is_delete = 0")
    List<UserSkillStatusDO> selectByUserId(@Param("userId") Long userId);

    /**
     * 更新用户Skill状态
     */
    @Update("UPDATE user_skill_status SET status = #{status}, gmt_modified = NOW() WHERE user_id = #{userId} AND skill_id = #{skillId}")
    int updateStatus(@Param("userId") Long userId, @Param("skillId") String skillId, @Param("status") Integer status);

    /**
     * 软删除用户Skill状态
     */
    @Update("UPDATE user_skill_status SET is_delete = 1, gmt_modified = NOW() WHERE user_id = #{userId} AND skill_id = #{skillId}")
    int softDelete(@Param("userId") Long userId, @Param("skillId") String skillId);
}
