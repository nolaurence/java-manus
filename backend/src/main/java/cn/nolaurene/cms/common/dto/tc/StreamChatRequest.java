package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

import java.util.List;

@Data
public class StreamChatRequest {

    /**
     * 只用于流失输出，暂时没啥用
     */
    private boolean stream;

    /**
     * 也没用
     */
    private String model;

    private List<MessageRequest> messages;
}
