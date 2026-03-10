package cn.nolaurene.cms.dal.mapper;

import cn.nolaurene.cms.dal.entity.SkillDocumentDO;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Skill文档Mapper
 *
 * @author nolaurence
 */
public interface SkillDocumentMapper extends Mapper<SkillDocumentDO, Long> {

    /**
     * 根据skillId查询所有文档
     */
    @Select("SELECT * FROM skill_document WHERE skill_id = #{skillId} AND is_delete = 0 ORDER BY sort_order ASC")
    List<SkillDocumentDO> selectBySkillId(@Param("skillId") String skillId);

    /**
     * 根据skillId和文档类型查询
     */
    @Select("SELECT * FROM skill_document WHERE skill_id = #{skillId} AND doc_type = #{docType} AND is_delete = 0 ORDER BY sort_order ASC")
    List<SkillDocumentDO> selectBySkillIdAndType(@Param("skillId") String skillId, @Param("docType") String docType);

    /**
     * 软删除指定Skill的所有文档
     */
    @Update("UPDATE skill_document SET is_delete = 1, gmt_modified = NOW() WHERE skill_id = #{skillId}")
    int softDeleteBySkillId(@Param("skillId") String skillId);

    /**
     * 软删除指定文档
     */
    @Update("UPDATE skill_document SET is_delete = 1, gmt_modified = NOW() WHERE id = #{id}")
    int softDeleteById(@Param("id") Long id);
}
