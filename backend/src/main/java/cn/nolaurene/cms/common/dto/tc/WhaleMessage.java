package cn.nolaurene.cms.common.dto.tc;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

/**
 * @author guofukang.gfk
 * @date 2025/3/21.
 */
@Data
public class WhaleMessage {

    private JSONObject jsonObject;

    public WhaleMessage(String role, String content) {
        jsonObject = new JSONObject();
//        jsonObject.put(role, content);
        jsonObject.put("role", role);
        jsonObject.put("content", content);
    }

    public static JSONObject of(String role, String content) {
        return new WhaleMessage(role, content).getJsonObject();
    }

    public static JSONObject ofUser(String content) {
        return new WhaleMessage("user", content).getJsonObject();
    }
}
