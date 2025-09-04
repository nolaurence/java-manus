package cn.nolaurene.cms.common.dto;

import lombok.Data;

@Data
public class ClientQueryRequest {

    private String name;

    private String creator;

    private Integer currentPage;

    private Integer pageSize;
}
