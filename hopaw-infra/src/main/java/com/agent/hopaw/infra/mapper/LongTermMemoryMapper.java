package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.LongTermMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LongTermMemoryMapper {

    List<LongTermMemory> findByParentId(@Param("parentId") Long parentId);

    List<LongTermMemory> findBySessionIdAndUserIdAndMemoryTypeAndTime(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("memoryType") String memoryType, @Param("beginDateTime") LocalDateTime beginDateTime);
    List<LongTermMemory> findBySessionIdAndUserIdAndMemoryTypeAndEndDateTime(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("memoryType") String memoryType, @Param("endDateTime") LocalDateTime endDateTime);

    List<LongTermMemory> findByUserIdAndMemoryType(@Param("userId") String userId, @Param("memoryType") String memoryType);

    LongTermMemory findById(@Param("id") Long id);

    int insert(LongTermMemory memory);

    int update(LongTermMemory memory);

    int deleteById(@Param("id") Long id);

    List<LongTermMemory> findByUserId(@Param("userId") String userId);

    int deleteByUserId(@Param("userId") String userId);

    int updateParentId(@Param("id") Long id, @Param("parentId") Long parentId);

    int deleteBySessionIdAndUserIdAndMemoryTypeAndEndDateTime(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("memoryType") String memoryType, @Param("endDateTime") LocalDateTime endDateTime);

    /**
     * 查询在指定截止时间之前的会话+用户+类型记忆，仅用于过期清理时获取 embeddingId 同步清理向量库
     */
    List<LongTermMemory> findExpiredBySessionIdAndUserIdAndMemoryTypeAndEndDateTime(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("memoryType") String memoryType, @Param("endDateTime") LocalDateTime endDateTime);
}
