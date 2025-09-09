package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.common.dto.ConversationResponse;
import cn.nolaurene.cms.common.dto.SessionSummary;
import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    @Resource
    private ConversationHistoryService conversationHistoryService;

    /**
     * 获取某个用户的所有会话摘要
     */
    @GetMapping("/sessions")
    public Response<List<SessionSummary>> getUserSessions(@RequestParam("userId") String userId) {
        if (StringUtils.isBlank(userId)) {
            return Response.error("userId is required", Collections.emptyList());
        }
        List<SessionSummary> summaries = conversationHistoryService.getUserSessionSummaries(userId);
        return Response.success(summaries);
    }

    /**
     * 获取指定会话的消息列表
     */
    @GetMapping("/messages")
    public Response<List<ConversationResponse>> getSessionMessages(@RequestParam("sessionId") String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return Response.error("sessionId is required", Collections.emptyList());
        }
        List<ConversationResponse> messages = conversationHistoryService.getSessionConversations(sessionId);
        return Response.success(messages);
    }
}

