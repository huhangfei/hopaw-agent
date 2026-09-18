package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.ChatMemoryStatusEnum;
import com.agent.hopaw.infra.model.dto.ChatMemoryVO;
import com.agent.hopaw.infra.model.entity.ChatMemory;
import com.agent.hopaw.infra.model.entity.ChatMemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.List;

/**
 * @author hhf
 */
public interface IChatMemoryService extends ChatMemoryStore {

    /**
     * @param sessionId
     * @param status
     * @return
     */
    List<ChatMemory> findBySessionIdAndStatus(String sessionId,List<Integer> status);

    /**
     * @param ids
     * @return
     */
    int deleteByIds(List<Long> ids);
    /**
     * 清理历史孤儿信息，同时将状态1的消息转到状态3
     * @param memoryId
     */
    void orphanCleanup(ChatMemoryId memoryId);


    void clear(String sessionId);

    /**
     * @param sessionId
     * @param requestId
     * @param status
     * @param newStatus
     * @return
     */
    int updateStatusBySessionIdAndRequestId(String sessionId, String requestId, ChatMemoryStatusEnum status, ChatMemoryStatusEnum newStatus);

    /**
     * 按会话编号查询记忆列表，解析 messageJson 为各类型结构化数据（system/user/ai/toolResult）
     *
     * @param sessionId 会话编号
     * @return 按创建时间升序的记忆列表
     */
    List<ChatMemoryVO> getChatMemoryVosBySessionId(String sessionId);
}
