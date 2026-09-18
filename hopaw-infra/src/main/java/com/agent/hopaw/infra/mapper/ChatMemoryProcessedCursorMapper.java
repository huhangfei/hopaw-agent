package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ChatMemoryProcessedCursor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMemoryProcessedCursorMapper {

    /**
     * 根据 sessionId 与 userId 查询进度游标。
     *
     * @param sessionId 会话编号
     * @param userId    用户编号
     * @return 进度游标（不存在时返回 null）
     */
    ChatMemoryProcessedCursor findBySessionIdAndUserId(@Param("sessionId") String sessionId,
                                                       @Param("userId") String userId);

    /**
     * 插入或更新（按主键 session_id + user_id）进度游标。
     */
    int upsert(ChatMemoryProcessedCursor cursor);
}
