package cn.nolaurene.cms.service.sandbox.backend.message;

import cn.nolaurene.cms.common.dto.ConversationRequest;
import cn.nolaurene.cms.common.dto.ConversationResponse;
import cn.nolaurene.cms.common.dto.PageInfo;
import cn.nolaurene.cms.common.dto.SessionSummary;
import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import cn.nolaurene.cms.common.sandbox.backend.model.data.*;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.enhance.mapper.ConversationHistoryMapper;
import cn.nolaurene.cms.dal.entity.ConversationInfoDO;
import cn.nolaurene.cms.dal.mapper.ConversationHistoryTkMapper;
import cn.nolaurene.cms.dal.mapper.ConversationInfoMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.mybatis.mapper.example.Example;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
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

    @Resource
    private ConversationInfoMapper conversationInfoMapper;

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
                .eventType(request.getEventType().getType())
                .content(request.getContent())
                .metadata(request.getMetadata())
                .isDeleted(false)
                .build();

        conversationHistoryTkMapper.insertSelective(conversation);
        return convertToResponse(conversation);
    }

    public void updateLastPlan(String sessionId, Plan plan) {
        Example<ConversationHistoryDO> example = new Example<>();
        example.createCriteria()
                .andEqualTo(ConversationHistoryDO::getSessionId, sessionId)
                .andEqualTo(ConversationHistoryDO::getEventType, SSEEventType.PLAN.getType());

        example.orderByDesc(ConversationHistoryDO::getGmtCreate);

        List<ConversationHistoryDO> planMessageList = conversationHistoryTkMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(planMessageList)) {
            return;
        }

        // filter some fields in plan object
        Plan newPlan = new Plan(plan.getMessage(), plan.getGoal(), plan.getTitle(), new ArrayList<>(plan.getSteps()));
        newPlan.getSteps().forEach(step -> {
            step.setResult(null);
            step.setError(null);
        });

        ConversationHistoryDO planMessage = planMessageList.get(0);
        ConversationHistoryDO newDataObject = new ConversationHistoryDO();
        newDataObject.setId(planMessage.getId());
        newDataObject.setContent(JSON.toJSONString(plan));
        conversationHistoryTkMapper.updateByPrimaryKeySelective(newDataObject);
    }

    public void addStep(String userId, String sessionId, String stepDescription) {
        ConversationHistoryDO conversation = ConversationHistoryDO.builder()
                .userId(StringUtils.isNoneBlank(userId) ? userId : "anonymous")
                .sessionId(sessionId)
                .messageType(ConversationHistoryDO.MessageType.ASSISTANT)
                .eventType(SSEEventType.STEP.getType())
                .content(stepDescription)
                .isDeleted(false)
                .build();

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("stepStatus", StepEventStatus.running.getCode());
        conversation.setMetadata(jsonObject.toString());
        conversationHistoryTkMapper.insertSelective(conversation);
    }

    public void updateLastStepStatus(String sessionId, String status) {
        Example<ConversationHistoryDO> example = new Example<>();
        example.createCriteria().andEqualTo(ConversationHistoryDO::getSessionId, sessionId)
                .andEqualTo(ConversationHistoryDO::getEventType, SSEEventType.STEP.getType())
                .andEqualTo(ConversationHistoryDO::getIsDeleted, false);
        example.orderByDesc(ConversationHistoryDO::getGmtModified);
        List<ConversationHistoryDO> stepMessageList = conversationHistoryTkMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(stepMessageList)) {
            return;
        }
        ConversationHistoryDO stepMessage = stepMessageList.get(0);
        ConversationHistoryDO newDataObject = new ConversationHistoryDO();
        newDataObject.setId(stepMessage.getId());

        JSONObject metaData = JSON.parseObject(stepMessage.getMetadata());
        metaData.put("stepStatus", status);
        newDataObject.setMetadata(metaData.toString());
        conversationHistoryTkMapper.updateByPrimaryKeySelective(newDataObject);
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
     * upsert conversation info
     */
    @Transactional
    public void upsertConversationInfo(ConversationInfoDO conversationInfo) {
        log.debug("upsert conversation info: {}", conversationInfo);
        Optional<ConversationInfoDO> conversationInfoDO = conversationInfoMapper.selectByPrimaryKey(conversationInfo.getSessionId());
        if (conversationInfoDO.isPresent() && conversationInfoDO.get().getGmtDeleted() == null) {
            ConversationInfoDO dataObject = conversationInfoDO.get();

            ConversationInfoDO newDataObject = new ConversationInfoDO();
            newDataObject.setSessionId(dataObject.getSessionId());
            newDataObject.setTitle(conversationInfo.getTitle());
            newDataObject.setStatus(conversationInfo.getStatus());
            newDataObject.setGmtModified(new Date());

            conversationInfoMapper.updateByPrimaryKeySelective(newDataObject);
        }

        ConversationInfoDO newDataObject = new ConversationInfoDO();
        newDataObject.setSessionId(conversationInfo.getSessionId());
        newDataObject.setTitle(conversationInfo.getTitle());
        newDataObject.setStatus(conversationInfo.getStatus());
        newDataObject.setGmtCreate(new Date());
        newDataObject.setGmtModified(new Date());
        conversationInfoMapper.insertSelective(newDataObject);
    }

    /**
     * 转换为响应DTO
     */
    private ConversationResponse convertToResponse(ConversationHistoryDO conversation) {
        ConversationResponse response = ConversationResponse.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .sessionId(conversation.getSessionId())
                .messageType(conversation.getMessageType())
                .metadata(conversation.getMetadata())
                .createdTime(conversation.getGmtCreate())
                .updatedTime(conversation.getGmtModified())
                .build();
        // process content
        switch(SSEEventType.fromType(conversation.getEventType())) {
            case MESSAGE:
                MessageEventData messageEventData = new MessageEventData();
                messageEventData.setContent(conversation.getContent());
                response.setContent(messageEventData);
                break;
            case PLAN:
                Plan plan = JSON.parseObject(conversation.getContent(), Plan.class);
                PlanEventData planEventData = new PlanEventData();
                planEventData.setId(String.valueOf(System.currentTimeMillis()));
                planEventData.setTitle(plan.getTitle());
                planEventData.setGoal(plan.getGoal());
                planEventData.setStatus("created");
                planEventData.setSteps(plan.getSteps().stream().map(step -> {
                    StepEventData stepData = new StepEventData();
                    stepData.setDescription(step.getDescription());
                    stepData.setStatus(step.getStatus());
                    stepData.setTimestamp(System.currentTimeMillis());
                    return stepData;
                }).collect(Collectors.toList()));
                response.setContent(planEventData);
                break;
            case STEP:
                Step step = JSON.parseObject(conversation.getContent(), Step.class);
                StepEventData stepEventData = new StepEventData();
                stepEventData.setTimestamp(System.currentTimeMillis());
                stepEventData.setStatus(JSON.parseObject(conversation.getMetadata()).getString("stepStatus"));
                stepEventData.setDescription(conversation.getContent());
                response.setContent(stepEventData);
                break;
            case TOOL:
                ToolEventData toolEventData = JSON.parseObject(conversation.getContent(), ToolEventData.class);
                response.setContent(toolEventData);
        }
        return response;
    }
}
