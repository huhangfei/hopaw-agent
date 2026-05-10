package com.agent.hopaw.mapper;

import com.agent.hopaw.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.model.LongTermMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LongTermMemoryMapper {
    List<LongTermMemory> findByAgentId(@Param("agentId") Long agentId);

    List<LongTermMemory> findByAgentIdAndUserId(@Param("agentId") Long agentId, @Param("userId") String userId);

    List<LongTermMemory> findByParentId(@Param("parentId") Long parentId);

    List<LongTermMemory> findByAgentIdAndUserIdAndMemoryTypeAndTime(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("memoryType") String memoryType, @Param("beginDateTime") LocalDateTime beginDateTime);
    List<LongTermMemory> getRecentActivityMemoriesByUserIdAndTypesAndTime(@Param("agentId") Long agentId,@Param("userId") String userId, @Param("memoryTypes") List<LongTermMemoryTypeEnum> memoryTypes, @Param("beginDateTime") LocalDateTime beginDateTime);

    List<LongTermMemory> findByAgentIdAndMemoryType(@Param("agentId") Long agentId, @Param("memoryType") String memoryType);
    List<LongTermMemory> findRootsByAgentIdAndUserId(@Param("agentId") Long agentId,@Param("userId") String userId);

    List<LongTermMemory> findByUserIdAndMemoryType(@Param("userId") String userId, @Param("memoryType") String memoryType);

    LongTermMemory findById(@Param("id") Long id);

    int insert(LongTermMemory memory);

    int update(LongTermMemory memory);

    int deleteById(@Param("id") Long id);

    int deleteByAgentId(@Param("agentId") Long agentId);

    int updateParentId(@Param("id") Long id, @Param("parentId") Long parentId);
}
