package cn.nolaurene.cms.common.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationInfo {

    private String userId;

    private String sessionId;

    private String title;
}
