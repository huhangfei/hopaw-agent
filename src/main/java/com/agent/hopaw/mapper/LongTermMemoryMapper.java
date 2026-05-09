package com.agent.hopaw.mapper;

import com.agent.hopaw.model.LongTermMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LongTermMemoryMapper {
    List<LongTermMemory> findByAgentId(@Param("agentId") String agentId);

    List<LongTermMemory> findByAgentIdAndUserId(@Param("agentId") String agentId, @Param("userId") String userId);

    List<LongTermMemory> findByAgentIdAndParentId(@Param("agentId") String agentId, @Param("parentId") Long parentId);

    List<LongTermMemory> findByAgentIdAndUserIdAndMemoryTypeAndTime(@Param("agentId") String agentId, @Param("userId") String userId, @Param("memoryType") String memoryType, @Param("beginDateTime") LocalDateTime beginDateTime);

    List<LongTermMemory> findByAgentIdAndMemoryType(@Param("agentId") String agentId, @Param("memoryType") String memoryType);
    List<LongTermMemory> findRootsByAgentIdAndUserId(@Param("agentId") String agentId,@Param("userId") String userId);

    LongTermMemory findByAgentIdAndHash(@Param("agentId") String agentId, @Param("hash") String hash);

    LongTermMemory findById(@Param("id") Long id);

    int insert(LongTermMemory memory);

    int update(LongTermMemory memory);

    int deleteById(@Param("id") Long id);

    int deleteByAgentId(@Param("agentId") String agentId);

    int updateParentId(@Param("id") Long id, @Param("parentId") Long parentId);
}
