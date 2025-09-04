package cn.nolaurene.cms.common.dto;

import lombok.Data;

@Data
public class UserSearchRequest {

    private String account;

    private String name;

    private Long userid;

    private int current;

    private int pageSize;
}
