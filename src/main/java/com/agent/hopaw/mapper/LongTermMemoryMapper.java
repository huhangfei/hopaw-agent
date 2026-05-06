package com.agent.hopaw.mapper;

import com.agent.hopaw.model.LongTermMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LongTermMemoryMapper {
    List<LongTermMemory> findByAgentId(@Param("agentId") String agentId);


    List<LongTermMemory> findByAgentIdAndParentId(@Param("agentId") String agentId, @Param("parentId") Long parentId);
    List<LongTermMemory> findRootsByAgentIdAndUserId(@Param("agentId") String agentId,@Param("userId") String userId);

    LongTermMemory findByAgentIdAndHash(@Param("agentId") String agentId, @Param("hash") String hash);

    LongTermMemory findById(@Param("id") Long id);

    int insert(LongTermMemory memory);

    int update(LongTermMemory memory);

    int deleteById(@Param("id") Long id);

    int deleteByAgentId(@Param("agentId") String agentId);
}
