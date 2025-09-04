package cn.nolaurene.cms.common.vo.tc;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class StyledTag {

    private String text;

    private JSONObject style;
}
