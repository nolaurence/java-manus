package cn.nolaurene.cms.common.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class SalesRecord {

    private Long id;

    private String description;

    private BigDecimal amount;

    private String imageURL;

    private String clientName;

    private Long clientId;

    private String creator;

    private String modifier;

    private String gmtCreate;
}
