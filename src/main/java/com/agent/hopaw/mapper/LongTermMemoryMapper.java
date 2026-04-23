package com.agent.hopaw.mapper;

import com.agent.hopaw.model.LongTermMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LongTermMemoryMapper {
    List<LongTermMemory> findByIdentity(@Param("identity") String identity);


    List<LongTermMemory> findByIdentityAndParentId(@Param("identity") String identity, @Param("parentId") Long parentId);

    LongTermMemory findByIdentityAndMemory(@Param("identity") String identity, @Param("memory") String memory);

    LongTermMemory findById(@Param("id") Long id);

    int insert(LongTermMemory memory);

    int update(LongTermMemory memory);

    int deleteById(@Param("id") Long id);

    int deleteByIdentity(@Param("identity") String identity);
}
