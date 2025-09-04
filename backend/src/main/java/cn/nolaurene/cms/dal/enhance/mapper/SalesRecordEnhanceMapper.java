package cn.nolaurene.cms.dal.enhance.mapper;

import cn.nolaurene.cms.dal.enhance.entity.SalesRecordWithClientDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface SalesRecordEnhanceMapper {

    List<SalesRecordWithClientDTO> selectSalesRecordWithClient(
            @Param("creator") String creator,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime,
            @Param("clientId") Long clientId,
            @Param("isDeleted") Integer isDeleted
    );
}