package cn.nolaurene.cms.converter;

import cn.nolaurene.cms.common.vo.SalesRecord;
import cn.nolaurene.cms.dal.enhance.entity.SalesRecordWithClientDTO;
import cn.nolaurene.cms.dal.entity.SalesRecordDO;
import cn.nolaurene.cms.utils.DateUtils;

public class SalesRecordConverter {

    public static SalesRecord fromSalesRecordDO(SalesRecordDO databaseObject) {
        SalesRecord viewObject = new SalesRecord();
        viewObject.setId(databaseObject.getId());
        viewObject.setDescription(databaseObject.getDescription());
        viewObject.setAmount(databaseObject.getAmount());
        viewObject.setImageURL(databaseObject.getImage());
        viewObject.setCreator(databaseObject.getCreatorName());
        viewObject.setModifier(databaseObject.getModifierName());
        viewObject.setGmtCreate(DateUtils.transferToNormalFormat(databaseObject.getGmtCreate()));

        return viewObject;
    }

    public static SalesRecord fromEnhancedDataObject(SalesRecordWithClientDTO enhancedDataObject) {
        SalesRecord viewObject = new SalesRecord();
        viewObject.setId(enhancedDataObject.getId());
        viewObject.setDescription(enhancedDataObject.getDescription());
        viewObject.setAmount(enhancedDataObject.getAmount());
        viewObject.setImageURL(enhancedDataObject.getImage());
        viewObject.setClientName(enhancedDataObject.getClientName());
        viewObject.setClientId(enhancedDataObject.getClientId());
        viewObject.setCreator(enhancedDataObject.getCreatorName());
        viewObject.setModifier(enhancedDataObject.getModifierName());
        viewObject.setGmtCreate(DateUtils.transferToNormalFormat(enhancedDataObject.getGmtCreate()));

        return viewObject;
    }
}
