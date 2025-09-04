package cn.nolaurene.cms.dal.enhance.mapper;

import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO;
import cn.nolaurene.cms.dal.enhance.entity.ConversationHistoryDO.MessageType;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationHistoryMapper {

    /**
     * 插入对话历史
     */
    int insert(ConversationHistoryDO conversation);

    /**
     * 批量插入对话历史
     */
    int insertBatch(@Param("conversations") List<ConversationHistoryDO> conversations);

    /**
     * 根据ID查询对话历史
     */
    ConversationHistoryDO selectById(@Param("id") Long id);

    /**
     * 根据用户ID查询对话历史(分页)
     */
    List<ConversationHistoryDO> selectByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID和会话ID查询对话历史
     */
    List<ConversationHistoryDO> selectByUserIdAndSessionId(@Param("userId") String userId,
                                                         @Param("sessionId") String sessionId);

    /**
     * 根据会话ID查询对话历史
     */
    List<ConversationHistoryDO> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据用户ID查询所有会话ID
     */
    List<String> selectDistinctSessionIdsByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID和消息类型查询对话历史
     */
    List<ConversationHistoryDO> selectByUserIdAndMessageType(@Param("userId") String userId,
                                                           @Param("messageType") MessageType messageType);

    /**
     * 根据时间范围查询对话历史
     */
    List<ConversationHistoryDO> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 根据用户ID和时间范围查询对话历史
     */
    List<ConversationHistoryDO> selectByUserIdAndTimeRange(@Param("userId") String userId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 统计用户的对话数量
     */
    Long countByUserId(@Param("userId") String userId);

    /**
     * 统计会话的消息数量
     */
    Long countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据内容关键词搜索对话历史
     */
    List<ConversationHistoryDO> searchByContentKeyword(@Param("userId") String userId,
                                                       @Param("keyword") String keyword);

    /**
     * 获取用户最近的对话历史
     */
    List<ConversationHistoryDO> selectRecentByUserId(@Param("userId") String userId,
                                                   @Param("limit") int limit);

    /**
     * 获取会话的最后一条消息
     */
    ConversationHistoryDO selectLastMessageBySessionId(@Param("sessionId") String sessionId);

    /**
     * 软删除指定会话的所有消息
     */
    int softDeleteBySessionId(@Param("sessionId") String sessionId,
                              @Param("updateTime") LocalDateTime updateTime);

    /**
     * 软删除用户的所有对话历史
     */
    int softDeleteByUserId(@Param("userId") String userId,
                           @Param("updateTime") LocalDateTime updateTime);

    /**
     * 物理删除指定时间之前的对话历史
     */
    int deleteByCreatedTimeBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 更新对话历史
     */
    int update(ConversationHistoryDO conversation);
}
