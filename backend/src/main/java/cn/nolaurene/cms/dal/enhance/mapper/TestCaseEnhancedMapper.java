package cn.nolaurene.cms.dal.enhance.mapper;

import cn.nolaurene.cms.dal.entity.CaseTestCaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TestCaseEnhancedMapper {

    int batchInsert(@Param("caseTestCaseList") List<CaseTestCaseDO> caseTestCaseList);

    List<CaseTestCaseDO> selectAllByRootId(@Param("rootId") Long rootId);

    int batchUpdate(List<CaseTestCaseDO> caseTestCaseDOS);
}
