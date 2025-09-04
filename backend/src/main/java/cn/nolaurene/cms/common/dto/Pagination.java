package cn.nolaurene.cms.common.dto;

import lombok.Data;

@Deprecated
@Data
public class Pagination {

    private int current;

    private int pageSize;

    private long total;

    public static Pagination of(int current, int pageSize, long total) {
        Pagination pagination = new Pagination();
        pagination.setCurrent(current);
        pagination.setPageSize(pageSize);
        pagination.setTotal(total);
        return pagination;
    }
}
