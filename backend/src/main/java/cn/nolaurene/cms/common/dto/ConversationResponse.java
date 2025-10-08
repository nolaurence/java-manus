package cn.nolaurene.cms.common.dto;

import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationResponse {

    private Long id;
    private String userId;
    private String sessionId;
    private MessageType messageType;
    private Object content;
    private String metadata;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
