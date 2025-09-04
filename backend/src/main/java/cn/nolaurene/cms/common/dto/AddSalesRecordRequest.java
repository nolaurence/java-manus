package cn.nolaurene.cms.common.dto;

import lombok.Data;

@Data
public class AddSalesRecordRequest {

    private String description;

    private Double amount;

    private String imageURL;

    private Long clientId;
}
