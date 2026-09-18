package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.constant.ChatMemoryStatusEnum;
import com.agent.hopaw.infra.model.entity.ChatMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatMemoryMapper {
    List<ChatMemory> findBySessionId(@Param("sessionId") String agentId);

    List<ChatMemory> findByAgentIdAndUserId(@Param("agentId") Long agentId, @Param("userId") String userId);

    List<ChatMemory> findByAgentIdAndUserIdInStatus(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("status") List<Integer> status);

    List<ChatMemory> findBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") List<Integer> status);

    List<ChatMemory> findByAgentIdAndUserIdAndStatus(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("status") Integer status);

    int insert(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("messageId") String messageId, @Param("messageJson") String messageJson, @Param("sessionId") String sessionId, @Param("requestId") String requestId, @Param("createTime") LocalDateTime createTime);

    int updateByMessageId(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("messageId") String messageId, @Param("messageJson") String messageJson);

    int updateStatusByAgentId(@Param("agentId") Long agentId, @Param("status") Integer status);

    int updateStatusBySessionId(@Param("sessionId") String sessionId, @Param("status") Integer status);

    int updateStatusBySessionIdAndRequestId(@Param("sessionId") String sessionId, @Param("requestId") String requestId, @Param("status") Integer status,@Param("newStatus") Integer newStatus);

    int deleteByMessageId(@Param("sessionId") String sessionId, @Param("userId") String userId,  @Param("messageId") String messageId);

    int deleteByAgentId(@Param("agentId") Long agentId);

    int deleteBySessionIdAndUserId(@Param("sessionId") String sessionId, @Param("userId") String userId);

    int deleteByIds(@Param("ids") List<Long> ids);

    int updateStatus(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("messageId") String messageId, @Param("status") Integer status);

    int updateStatusByStatus(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("oldStatus")Integer oldStatus, @Param("status") Integer status);

    List<ChatMemory> findObsoleteDistinctSessionUserPairs();


    /**
     * 查询 chat_memory 中指定状态的所有 (session_id, user_id) 组合。
     * 用于发现那些从未被转移到 chat_memory_obsolete 但已具备整理条件的会话。
     */
    List<ChatMemory> findTaskDoneDistinctSessionUserPairs(@Param("status") Integer status);


    /**
     * 查询会话根据会话和用户编号
     * @param sessionId
     * @param userId
     * @return
     */
    List<ChatMemory> findObsoleteChatMemoryBySessionIdAndUserId(String sessionId,String userId);


    /**
     * 增量查询：拉取指定会话/用户下 status = TASK_DONE 且 id 大于 lastId 的数据。
     * 用于将长期滞留在 chat_memory 中尚未转移的已结束任务纳入整理。
     *
     * @param sessionId 会话编号
     * @param userId    用户编号
     * @param status    期望的状态列表
     * @param lastId    上次已处理到的最大 id（不包含）
     * @return 增量数据，按 id 升序
     */
    List<ChatMemory> findTaskDoneChatMemoryBySessionIdAndUserIdAfterId(@Param("sessionId") String sessionId,
                                                                       @Param("userId") String userId,
                                                                       @Param("status") List<Integer> status,
                                                                       @Param("lastId") Long lastId);


    /**
     * @param ids
     * @return
     */
    int deleteObsoleteChatMemoryByIds(@Param("ids") List<Long> ids);
}
