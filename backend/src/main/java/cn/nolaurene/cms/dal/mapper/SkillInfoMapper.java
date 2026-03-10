package cn.nolaurene.cms.dal.mapper;

import cn.nolaurene.cms.dal.entity.SkillInfoDO;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Skill信息Mapper
 *
 * @author nolaurence
 */
public interface SkillInfoMapper extends Mapper<SkillInfoDO, Long> {

    /**
     * 根据skillId查询
     */
    @Select("SELECT * FROM skill_info WHERE skill_id = #{skillId} AND is_delete = 0")
    SkillInfoDO selectBySkillId(@Param("skillId") String skillId);

    /**
     * 查询所有活跃的Skill
     */
    @Select("SELECT * FROM skill_info WHERE status = 1 AND is_delete = 0 ORDER BY priority DESC")
    List<SkillInfoDO> selectAllActive();

    /**
     * 根据分类查询
     */
    @Select("SELECT * FROM skill_info WHERE category = #{category} AND status = 1 AND is_delete = 0 ORDER BY priority DESC")
    List<SkillInfoDO> selectByCategory(@Param("category") String category);

    /**
     * 根据用户ID查询
     */
    @Select("SELECT * FROM skill_info WHERE user_id = #{userId} AND is_delete = 0 ORDER BY priority DESC, gmt_create DESC")
    List<SkillInfoDO> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据作者查询
     */
    @Select("SELECT * FROM skill_info WHERE author = #{author} AND status = 1 AND is_delete = 0 ORDER BY priority DESC")
    List<SkillInfoDO> selectByAuthor(@Param("author") String author);

    /**
     * 软删除
     */
    @Update("UPDATE skill_info SET is_delete = 1, gmt_modified = NOW() WHERE skill_id = #{skillId}")
    int softDeleteBySkillId(@Param("skillId") String skillId);

    /**
     * 更新Skill
     */
    @Update("<script>" +
            "UPDATE skill_info SET gmt_modified = NOW() " +
            "<if test='description != null'>, description = #{description}</if>" +
            "<if test='category != null'>, category = #{category}</if>" +
            "<if test='priority != null'>, priority = #{priority}</if>" +
            "<if test='status != null'>, status = #{status}</if>" +
            "<if test='triggers != null'>, triggers = #{triggers}</if>" +
            "<if test='tools != null'>, tools = #{tools}</if>" +
            "<if test='requires != null'>, requires = #{requires}</if>" +
            "<if test='osSupport != null'>, os_support = #{osSupport}</if>" +
            "WHERE skill_id = #{skillId} AND is_delete = 0" +
            "</script>")
    int updateBySkillId(SkillInfoDO skillInfo);
}
