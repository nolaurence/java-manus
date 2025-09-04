package cn.nolaurene.cms.common.vo.tc;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.Date;

@Data
public class TagVO {

    private long id;

    private String tagName;

    private String creatorName;

    private Date gmtCreate;
}
