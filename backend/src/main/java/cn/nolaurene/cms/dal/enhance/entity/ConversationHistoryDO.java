package cn.nolaurene.cms.dal.enhance.entity;

import io.mybatis.provider.Entity.Table;
import io.mybatis.provider.Entity.Column;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("conversation_history")
public class ConversationHistoryDO {

    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    @Column("user_id")
    private String userId;

    @Column("session_id")
    private String sessionId;

    @Column("message_type")
    private MessageType messageType;

    @Column("event_type")
    private String eventType;

    @Column("content")
    private String content;

    @Column("metadata")
    private String metadata;

    @Column("gmt_create")
    private LocalDateTime gmtCreate;

    @Column("gmt_modified")
    private LocalDateTime gmtModified;

    @Column("is_deleted")
    private Boolean isDeleted;

    @Getter
    public enum MessageType {
        USER("用户消息"),
        ASSISTANT("AI回复");

        private final String description;

        MessageType(String description) {
            this.description = description;
        }
    }
}
