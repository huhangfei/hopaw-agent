package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {
    List<ChatSession> findAll();

    List<ChatSession> findByUserId(@Param("userId") String userId);

    /**
     * 分页查询用户会话（按最后更新时间倒序），可按 biz_type 集合过滤（会话清理设置页）
     *
     * @param bizTypes          允许的 biz_type 取值集合；null / 空集合表示不过滤
     * @param includeBlankBizType 是否把 biz_type 为 NULL / 空串的会话也算作命中（聊天分组需要）
     */
    List<ChatSession> findPageByUserIdWithFilters(@Param("userId") String userId,
                                                  @Param("bizTypes") List<String> bizTypes,
                                                  @Param("includeBlankBizType") boolean includeBlankBizType,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /** 用户的会话总数，过滤条件同上 */
    int countByUserIdWithFilters(@Param("userId") String userId,
                                 @Param("bizTypes") List<String> bizTypes,
                                 @Param("includeBlankBizType") boolean includeBlankBizType);

    List<ChatSession> findByUserIdAndAgentId(@Param("userId") String userId, @Param("agentId") Long agentId);

    /** 首页可见会话：用户自己的聊天会话 + 所有人的项目/工作流任务会话（兼容新旧 biz_type 值） */
    List<ChatSession> findVisibleSessions(@Param("userId") String userId, @Param("agentId") Long agentId);

    ChatSession findById(@Param("id") Long id);

    ChatSession findBySessionId(@Param("sessionId") String sessionId);

    int insert(ChatSession chatSession);

    int update(ChatSession chatSession);

    int updateTitle(@Param("id") Long id, @Param("title") String title);

    int deleteById(@Param("id") Long id);

    int deleteBySessionId(@Param("sessionId") String sessionId);

    int deleteByAgentId(@Param("agentId") Long agentId);

    int updateBizType(@Param("sessionId") String sessionId, @Param("bizType") String bizType);
}
