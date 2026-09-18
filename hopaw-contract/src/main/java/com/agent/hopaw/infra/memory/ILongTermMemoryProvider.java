package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.model.dto.MemorySearchResult;
import com.agent.hopaw.infra.model.entity.ChatMemory;

import java.util.List;

public interface ILongTermMemoryProvider {

    /**
     * 查询用户记忆
     * @param userId
     * @param keyword
     * @param maxResults
     * @return
     */
    List<MemorySearchResult> queryUserMemory(String userId,String keyword,Integer maxResults);

    /**
     * 接收用户临时记忆
     * @param chatMemories
     */
    void receiveUserTempMemory(List<ChatMemory> chatMemories);


    /**
     * 保存用户记忆
     * @param sessionId
     * @param userId
     * @param memoryType 记忆类型 UserMemoryTypeEnum
     * @param summary
     * @param memory
     */
    void saveUserMemory(String sessionId, String userId, UserMemoryTypeEnum memoryType, String summary, String memory);

}
