package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ChatMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMemoryObsoleteMapper {

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


    /**
     * @param chatMemoryList
     * @return
     */
    int insertBatch(@Param("list") List<ChatMemory> chatMemoryList);
}
