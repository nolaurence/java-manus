package cn.nolaurene.cms.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateSalesRecordRequest extends AddSalesRecordRequest {

    private Long id;
}
