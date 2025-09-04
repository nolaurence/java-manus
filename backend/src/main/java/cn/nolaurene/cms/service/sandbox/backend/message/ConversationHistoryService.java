package cn.nolaurene.cms.service.sandbox.backend.message;

import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.common.dto.ConversationResponse;
import cn.nolaurene.cms.common.dto.PageInfo;
import cn.nolaurene.cms.common.dto.SessionSummary;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.enhance.mapper.ConversationHistoryMapper;
import cn.nolaurene.cms.dal.mapper.ConversationHistoryTkMapper;
import io.mybatis.mapper.example.Example;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationHistoryService {

    @Resource
    private ConversationHistoryMapper conversationHistoryMapper;

    @Resource
    private ConversationHistoryTkMapper conversationHistoryTkMapper;

    /**
     * 保存对话历史
     */
    @Transactional
    public ConversationResponse saveConversation(ConversationRequest request) {
        log.info("保存对话历史: userId={}, sessionId={}, messageType={}",
                request.getUserId(), request.getSessionId(), request.getMessageType());

        ConversationHistoryDO conversation = ConversationHistoryDO.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .messageType(request.getMessageType())
                .content(request.getContent())
                .metadata(request.getMetadata())
                .isDeleted(false)
                .build();

        conversationHistoryMapper.insert(conversation);
        return convertToResponse(conversation);
    }

    /**
     * 批量保存对话历史
     */
    @Transactional
    public List<ConversationResponse> saveConversations(List<ConversationRequest> requests) {
        log.info("批量保存对话历史: 数量={}", requests.size());

        List<ConversationHistoryDO> conversations = requests.stream()
                .map(request -> ConversationHistoryDO.builder()
                        .userId(request.getUserId())
                        .sessionId(request.getSessionId())
                        .messageType(request.getMessageType())
                        .content(request.getContent())
                        .metadata(request.getMetadata())
                        .isDeleted(false)
                        .build())
                .collect(Collectors.toList());

        conversationHistoryMapper.insertBatch(conversations);
        return conversations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取对话历史
     */
    public Optional<ConversationResponse> getConversationById(Long id) {
        log.debug("根据ID获取对话历史: id={}", id);
        ConversationHistoryDO conversation = conversationHistoryMapper.selectById(id);
        return Optional.ofNullable(conversation).map(this::convertToResponse);
    }

    /**
     * 获取用户的对话历史(分页)
     */
    public PageInfo<ConversationResponse> getUserConversations(String userId, int page, int size) {
        log.debug("获取用户对话历史: userId={}, page={}, size={}", userId, page, size);

        RowBounds rowBounds = new RowBounds((page - 1) * size, size);
        Example<ConversationHistoryDO> example = new Example<>();
        example.createCriteria().andEqualTo(ConversationHistoryDO::getUserId, userId);

        List<ConversationHistoryDO> conversations = conversationHistoryTkMapper.selectByExample(example, rowBounds);
        PageInfo<ConversationHistoryDO> pageInfo = new PageInfo<>(conversations);

        long total = conversationHistoryTkMapper.countByExample(example);

        List<ConversationResponse> responses = conversations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        PageInfo<ConversationResponse> result = new PageInfo<>(responses);
        result.setTotal((int) total);
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());

        return result;
    }

    /**
     * 获取指定会话的对话历史
     */
    public List<ConversationResponse> getSessionConversations(String sessionId) {
        log.debug("获取会话对话历史: sessionId={}", sessionId);
        return conversationHistoryMapper.selectBySessionId(sessionId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户的所有会话摘要
     */
    public List<SessionSummary> getUserSessionSummaries(String userId) {
        log.debug("获取用户会话摘要: userId={}", userId);
        List<String> sessionIds = conversationHistoryMapper.selectDistinctSessionIdsByUserId(userId);

        return sessionIds.stream()
                .map(sessionId -> {
                    Long messageCount = conversationHistoryMapper.countBySessionId(sessionId);
                    ConversationHistoryDO lastMessage = conversationHistoryMapper
                            .selectLastMessageBySessionId(sessionId);

                    return SessionSummary.builder()
                            .sessionId(sessionId)
                            .userId(userId)
                            .messageCount(messageCount)
                            .lastMessageTime(lastMessage != null ? lastMessage.getGmtCreate() : null)
                            .lastMessage(lastMessage != null ?
                                    (lastMessage.getContent().length() > 100 ?
                                            lastMessage.getContent().substring(0, 100) + "..." :
                                            lastMessage.getContent()) : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据关键词搜索对话历史
     */
    public List<ConversationResponse> searchConversations(String userId, String keyword) {
        log.debug("搜索对话历史: userId={}, keyword={}", userId, keyword);
        return conversationHistoryMapper.searchByContentKeyword(userId, keyword)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户的最近对话
     */
    public List<ConversationResponse> getRecentConversations(String userId) {
        log.debug("获取用户最近对话: userId={}", userId);
        return conversationHistoryMapper.selectRecentByUserId(userId, 10)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 软删除会话
     */
    @Transactional
    public boolean deleteSession(String sessionId) {
        log.info("删除会话: sessionId={}", sessionId);
        int deletedCount = conversationHistoryMapper.softDeleteBySessionId(sessionId, LocalDateTime.now());
        return deletedCount > 0;
    }

    /**
     * 软删除用户的所有对话历史
     */
    @Transactional
    public boolean deleteUserConversations(String userId) {
        log.info("删除用户所有对话历史: userId={}", userId);
        int deletedCount = conversationHistoryMapper.softDeleteByUserId(userId, LocalDateTime.now());
        return deletedCount > 0;
    }

    /**
     * 物理删除指定时间之前的对话历史
     */
    @Transactional
    public void cleanupOldConversations(LocalDateTime cutoffTime) {
        log.info("清理旧对话历史: cutoffTime={}", cutoffTime);
        conversationHistoryMapper.deleteByCreatedTimeBefore(cutoffTime);
    }

    /**
     * 获取用户对话统计信息
     */
    public Long getUserConversationCount(String userId) {
        return conversationHistoryMapper.countByUserId(userId);
    }

    /**
     * 获取会话消息数量
     */
    public Long getSessionMessageCount(String sessionId) {
        return conversationHistoryMapper.countBySessionId(sessionId);
    }

    /**
     * 获取指定时间范围内的对话历史
     */
    public List<ConversationResponse> getConversationsByTimeRange(
            String userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("获取时间范围内的对话历史: userId={}, startTime={}, endTime={}",
                userId, startTime, endTime);
        return conversationHistoryMapper.selectByUserIdAndTimeRange(userId, startTime, endTime)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 转换为响应DTO
     */
    private ConversationResponse convertToResponse(ConversationHistoryDO conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .sessionId(conversation.getSessionId())
                .messageType(conversation.getMessageType())
                .content(conversation.getContent())
                .metadata(conversation.getMetadata())
                .createdTime(conversation.getGmtCreate())
                .updatedTime(conversation.getGmtModified())
                .build();
    }
}
