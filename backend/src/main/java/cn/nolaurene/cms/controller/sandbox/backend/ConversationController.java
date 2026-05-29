package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.common.dto.ConversationInfo;
import cn.nolaurene.cms.common.dto.ConversationResponse;
import cn.nolaurene.cms.common.dto.SessionSummary;
import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.dal.entity.ConversationInfoDO;
import cn.nolaurene.cms.dal.mapper.ConversationInfoMapper;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.service.UserLoginService;
import cn.nolaurene.cms.service.sandbox.backend.message.ConversationHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/conversations")
@Tag(name = "Conversation History Api")
public class ConversationController {

    @Resource
    private ConversationHistoryService conversationHistoryService;

    @Resource
    private ConversationInfoMapper conversationInfoMapper;

    @Resource
    private UserLoginService userLoginService;

    /**
     * 获取某个用户的所有会话摘要（从 conversation_info 获取）
     */
    @GetMapping("/sessions")
    public Response<List<SessionSummary>> getUserSessions(@RequestParam("userId") String userId, HttpServletRequest request) {
        if (StringUtils.isBlank(userId)) {
            return Response.error("userId is required", Collections.emptyList());
        }
        User currentUser = userLoginService.getCurrentUserInfo(request);
        if (currentUser == null || !userId.equals(String.valueOf(currentUser.getUserid()))) {
            return Response.error("未登录或无权访问该用户会话", Collections.emptyList());
        }
        List<ConversationInfoDO> infoList = conversationHistoryService.getUserConversationInfoList(userId);
        List<SessionSummary> summaries = infoList.stream()
                .map(info -> SessionSummary.builder()
                        .sessionId(info.getSessionId())
                        .userId(info.getUserId())
                        .title(info.getTitle())
                        .status(info.getStatus())
                        .build())
                .collect(Collectors.toList());
        return Response.success(summaries);
    }

    /**
     * 获取指定会话的消息列表
     */
    @GetMapping("/messages")
    public Response<List<ConversationResponse>> getSessionMessages(@RequestParam("sessionId") String sessionId, HttpServletRequest request) {
        if (StringUtils.isBlank(sessionId)) {
            return Response.error("sessionId is required", Collections.emptyList());
        }
        User currentUser = userLoginService.getCurrentUserInfo(request);
        if (currentUser == null) {
            return Response.error("未登录", Collections.emptyList());
        }
        List<ConversationResponse> messages = conversationHistoryService.getSessionConversations(sessionId);
        String currentUserId = String.valueOf(currentUser.getUserid());
        boolean hasForeignMessage = messages.stream()
                .anyMatch(message -> StringUtils.isNotBlank(message.getUserId()) && !currentUserId.equals(message.getUserId()));
        if (hasForeignMessage) {
            return Response.error("无权访问该会话", Collections.emptyList());
        }
        return Response.success(messages);
    }

    @GetMapping("/title")
    public Response<ConversationInfo> getBriefConversationInfo(@RequestParam String sessionId, HttpServletRequest request) {
        if (StringUtils.isBlank(sessionId)) {
            return Response.error("sessionId is required", null);
        }
        User currentUser = userLoginService.getCurrentUserInfo(request);
        if (currentUser == null) {
            return Response.error("未登录", null);
        }
        Optional<ConversationInfoDO> conversationInfoDO = conversationInfoMapper.selectByPrimaryKey(sessionId);
        if (conversationInfoDO.isPresent()) {
            ConversationInfoDO dataObject = conversationInfoDO.get();
            if (StringUtils.isNotBlank(dataObject.getUserId())
                    && !dataObject.getUserId().equals(String.valueOf(currentUser.getUserid()))) {
                return Response.error("无权访问该会话", null);
            }
            return Response.success(ConversationInfo.builder()
                    .sessionId(dataObject.getSessionId())
                    .userId(dataObject.getUserId())
                    .title(dataObject.getTitle())
                    .build());
        }
        return Response.error("Conversation not found", null);
    }
}
