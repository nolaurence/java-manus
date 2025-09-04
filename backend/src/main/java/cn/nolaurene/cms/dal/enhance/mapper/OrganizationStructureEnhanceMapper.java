package cn.nolaurene.cms.dal.enhance.mapper;

import cn.nolaurene.cms.dal.entity.OrganizationStructureDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author guofukang.gfk
 * @date 2024/11/1.
 */
public interface OrganizationStructureEnhanceMapper {

    List<OrganizationStructureDO> getOrganizationTree(@Param("rootOrgId") long rootOrgId);
}
