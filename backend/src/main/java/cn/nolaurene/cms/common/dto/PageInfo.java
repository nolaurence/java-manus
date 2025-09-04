package cn.nolaurene.cms.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageInfo<T> {

    private int total;

    private List<T> data;

    private int pageNum;

    private int pageSize;

    public PageInfo(List<T> data) {

        this.data = data;
    }
}
