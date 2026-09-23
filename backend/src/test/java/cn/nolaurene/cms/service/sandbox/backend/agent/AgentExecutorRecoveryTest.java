package cn.nolaurene.cms.service.sandbox.backend.agent;

import cn.nolaurene.cms.common.dto.ConversationResponse;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorRecoveryTest {

    @Test
    void onlyReplayableConversationMessagesEnterCopilotRecovery() {
        ConversationResponse toolAudit = ConversationResponse.builder()
                .messageType(ConversationHistoryDO.MessageType.ASSISTANT)
                .eventType(SSEEventType.TOOL)
                .content("{\"function\":\"read_file\",\"result\":\"ok\"}")
                .build();
        ConversationResponse reasoning = ConversationResponse.builder()
                .messageType(ConversationHistoryDO.MessageType.ASSISTANT)
                .eventType(SSEEventType.MESSAGE)
                .content("**Deep Thinking:** internal reasoning")
                .build();
        ConversationResponse assistant = ConversationResponse.builder()
                .messageType(ConversationHistoryDO.MessageType.ASSISTANT)
                .eventType(SSEEventType.MESSAGE)
                .content("final answer")
                .build();
        ConversationResponse user = ConversationResponse.builder()
                .messageType(ConversationHistoryDO.MessageType.USER)
                .eventType(SSEEventType.MESSAGE)
                .content("continue")
                .build();

        assertFalse(AgentExecutor.isCopilotRecoveryMessage(toolAudit));
        assertFalse(AgentExecutor.isCopilotRecoveryMessage(reasoning));
        // Rendered assistant events do not contain tool-call IDs and are
        // intentionally replayed from the last complete snapshot boundary.
        assertFalse(AgentExecutor.isCopilotRecoveryMessage(assistant));
        assertTrue(AgentExecutor.isCopilotRecoveryMessage(user));
    }
}
