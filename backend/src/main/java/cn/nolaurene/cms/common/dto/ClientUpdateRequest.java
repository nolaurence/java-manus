package cn.nolaurene.cms.common.dto;

import lombok.Data;

@Data
public class ClientUpdateRequest {

    private Long clientId;

    private String clientName;

    private String modifierAccount;
}
