package cn.nolaurene.cms.common.dto;

import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRequest {

    @NotBlank(message = "用户ID不能为空")
    @Size(max = 50, message = "用户ID长度不能超过50个字符")
    private String userId;

    @NotBlank(message = "会话ID不能为空")
    @Size(max = 100, message = "会话ID长度不能超过100个字符")
    private String sessionId;

    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;

    @NotNull(message = "事件类型不能为空")
    private SSEEventType eventType;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private String metadata;
}
