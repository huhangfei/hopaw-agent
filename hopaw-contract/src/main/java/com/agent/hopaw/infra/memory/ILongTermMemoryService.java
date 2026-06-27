package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.model.entity.ChatMemory;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import dev.langchain4j.invocation.InvocationParameters;

import java.util.List;
import java.util.function.Function;

public interface ILongTermMemoryService extends ILongTermMemoryProvider{


    List<LongTermMemory> getChildMemories(Long parentId);

    LongTermMemory getMemoryById(Long id);

    LongTermMemory createMemory(String sessionId, String memory, Long parentId, String userId,
                                UserMemoryTypeEnum memoryType, String summary);

    void deleteMemory(Long id);

    void deleteAllMemoriesByUserId(String userId);

    void update(LongTermMemory entity);

    void moveMemory(Long id, Long newParentId);

    List<LongTermMemory> getAllMemoriesByAgentId(String sessionId, String userId);

    void deleteExpiredTaskRecordsMemories(String sessionId, String userId);


    List<LongTermMemory> queryUserAllMemories(String sessionId, String userId);

    String queryUserAllMemoriesContent(String sessionId, String userId, Function<LongTermMemory, Boolean> includeDetailFun);

    String queryUserProfileMemoryContent(String userId);

    List<LongTermMemory> queryUserTaskRecordsMemory(String sessionId, String userId);

    String queryUserTaskRecordsMemoryContent(String sessionId, String userId, Boolean includeDetail);

    List<LongTermMemory> queryUserExpandKnowledgeMemory(String sessionId, String userId);

    String queryUserExpandKnowledgeMemoryContent(String sessionId, String userId, Boolean includeDetail);

    String buildMemoryContent(List<LongTermMemory> longTermMemories);

    String buildMemoryContent(List<LongTermMemory> longTermMemories, Boolean includeDetail);

    String buildMemoryContent(List<LongTermMemory> longTermMemories, Function<LongTermMemory, Boolean> includeDetailFun);

    String buildMemoryContent(LongTermMemory memory, Boolean includeDetail);

    String buildMemoryContent(LongTermMemory memory, Boolean includeDetail, Boolean includeType);

    String buildMemoryContent(LongTermMemory memory, Function<LongTermMemory, Boolean> includeDetailFun, Boolean includeType);

    String buildMemoryContent(LongTermMemory memory, Function<LongTermMemory, Boolean> includeDetailFun);

    String getMemoryContentById(Long id);

    String saveMemory(UserMemoryTypeEnum memoryType,
                      String summary,
                      String memory,
                      Long id,
                      InvocationParameters invocationParameters);

    String getMemoryOrganizingRules();
}
