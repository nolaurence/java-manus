package cn.nolaurene.cms.common.vo;

import cn.nolaurene.cms.common.dto.Pagination;
import lombok.Data;

import java.util.List;

@Deprecated
@Data
public class PagedData<T> {

    private List<T> list;

    private Pagination pagination;
}
