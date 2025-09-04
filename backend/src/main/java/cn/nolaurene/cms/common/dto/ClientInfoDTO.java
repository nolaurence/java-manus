package cn.nolaurene.cms.common.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ClientInfoDTO {

    private Long clientId;

    private String clientName;

    private String creatorAccount;

    private String modifierAccount;

    private Date gmtCreate;

    private Date gmtModified;
}
