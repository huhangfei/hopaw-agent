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
     * 查询会话根据会话和用户编号
     * @param sessionId
     * @param userId
     * @return
     */
    List<ChatMemory> findObsoleteChatMemoryBySessionIdAndUserId(String sessionId,String userId);


    /**
     * @param ids
     * @return
     */
    int deleteObsoleteChatMemoryByIds(@Param("ids") List<Long> ids);
}
