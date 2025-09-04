package cn.nolaurene.cms.dal.enhance.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class SalesRecordWithClientDTO {

    private Long id;

    private String description;

    private BigDecimal amount;

    private String image;

    private String creator;

    private String creatorName;

    private Long clientId;

    private String clientName; // 新增字段，用于存储客户名称

    private String modifier;

    private String modifierName;

    private Date gmtCreate;

    private Date gmtModified;

    private Boolean isDeleted;
}
