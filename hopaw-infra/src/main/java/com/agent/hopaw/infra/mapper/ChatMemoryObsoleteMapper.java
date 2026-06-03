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
     * 增量查询：根据 session / user 与上次处理到的最大 id，仅取大于该 id 的数据。
     *
     * @param sessionId    会话编号
     * @param userId       用户编号
     * @param lastId       上次已处理到的最大 id（不包含）
     * @return 增量数据，按 create_time 升序
     */
    List<ChatMemory> findObsoleteChatMemoryBySessionIdAndUserIdAfterId(@Param("sessionId") String sessionId,
                                                                       @Param("userId") String userId,
                                                                       @Param("lastId") Long lastId);


    /**
     * @param ids
     * @return
     */
    int deleteObsoleteChatMemoryByIds(@Param("ids") List<Long> ids);


    /**
     * 根据 session / user 删除 id 小于等于 maxId 的已处理数据。
     * 用于任务完成后清理本次已读取并整理的 chat_memory_obsolete 行。
     *
     * @param sessionId 会话编号
     * @param userId    用户编号
     * @param maxId     本次处理过的最大 id（包含）
     * @return 删除行数
     */
    int deleteObsoleteChatMemoryBySessionIdUserIdUpToId(@Param("sessionId") String sessionId,
                                                        @Param("userId") String userId,
                                                        @Param("maxId") Long maxId);


    /**
     * @param chatMemoryList
     * @return
     */
    int insertBatch(@Param("list") List<ChatMemory> chatMemoryList);
}
