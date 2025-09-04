package cn.nolaurene.cms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionSummary {

    private String sessionId;
    private String userId;
    private Long messageCount;
    private LocalDateTime lastMessageTime;
    private String lastMessage;
}
