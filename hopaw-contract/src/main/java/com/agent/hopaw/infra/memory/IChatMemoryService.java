package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.model.entity.ChatMemory;
import com.agent.hopaw.infra.model.entity.ChatMemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.List;

/**
 * @author hhf
 */
public interface IChatMemoryService extends ChatMemoryStore {
    List<ChatMemory> findDistinctSessionUserPairs();
    List<ChatMemory> findBySessionIdAndStatus(String sessionId,List<Integer> status);
    int deleteByIds(List<Long> ids);
    /**
     * 清理历史孤儿信息，同时将状态1的消息转到状态3
     * @param memoryId
     */
    void orphanCleanup(ChatMemoryId memoryId);

    int updateStatusBySessionId(String sessionId,Integer status);
}
