package com.agent.hopaw.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryProcessLogMapper {
    Long getLastProcessedChatId(@Param("identity") String identity);

    Integer upsert(@Param("identity") String identity, @Param("chatId") Long chatId);
}
