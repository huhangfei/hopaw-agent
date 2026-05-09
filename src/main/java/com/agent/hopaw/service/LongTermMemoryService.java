package com.agent.hopaw.service;

import com.agent.hopaw.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.mapper.LongTermMemoryMapper;
import com.agent.hopaw.model.LongTermMemory;
import com.agent.hopaw.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class LongTermMemoryService {

    private final LongTermMemoryMapper longTermMemoryMapper;
    private final SysConfigService sysConfigService;

    public LongTermMemoryService(LongTermMemoryMapper longTermMemoryMapper, SysConfigService sysConfigService) {
        this.longTermMemoryMapper = longTermMemoryMapper;
        this.sysConfigService = sysConfigService;
    }

    public List<LongTermMemory> getRootMemories(String agentId,String userId) {
        return longTermMemoryMapper.findRootsByAgentIdAndUserId(agentId,userId);
    }

    public List<LongTermMemory> getChildMemories(String agentId, Long parentId) {
        return longTermMemoryMapper.findByAgentIdAndParentId(agentId, parentId);
    }

    public LongTermMemory getMemoryById(Long id) {
        return longTermMemoryMapper.findById(id);
    }

    public LongTermMemory createMemory(String agentId, String memory, Long parentId, String userId) {
        return createMemory(agentId, memory, parentId, userId, null, null);
    }

    public LongTermMemory createMemory(String agentId, String memory, Long parentId, String userId,
                                        String memoryType, String summary) {
        LongTermMemory entity = new LongTermMemory(agentId, memory, parentId);
        entity.setUserId(userId);
        entity.setMemoryType(memoryType);
        entity.setSummary(summary);
        longTermMemoryMapper.insert(entity);
        return entity;
    }

    public LongTermMemory createMemory(String agentId, String memory, Long parentId) {
        return createMemory(agentId, memory, parentId, null, null, null);
    }


    public void deleteMemory(Long id) {
        LongTermMemory memory = longTermMemoryMapper.findById(id);
        longTermMemoryMapper.deleteById(id);
    }

    public void updateMemory(Long id, String memory) {
        LongTermMemory entity = longTermMemoryMapper.findById(id);
        if (entity != null) {
            entity.setMemory(memory);
            entity.setMemoryHash(String.valueOf(memory.hashCode()));
            longTermMemoryMapper.update(entity);
        }
    }

    public void moveMemory(Long id, Long newParentId) {
        longTermMemoryMapper.updateParentId(id, newParentId);
    }

    public List<LongTermMemory> getAllMemoriesByAgentId(String agentId, String userId) {
        return longTermMemoryMapper.findByAgentIdAndUserId(agentId, userId);
    }

    public List<LongTermMemory> getMemoriesByAgentIdAndUserIdAndMemoryType(String agentId, String userId, String memoryType,LocalDateTime beginDateTime) {
        return longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndTime(agentId, userId, memoryType, beginDateTime);
    }

    public List<LongTermMemory> getMemoriesByAgentIdAndUserId(String agentId, String userId, LocalDateTime beginDateTime) {
        return getMemoriesByAgentIdAndUserIdAndMemoryType(agentId, userId,null, beginDateTime);
    }
    public List<LongTermMemory> getRecentMemoriesByAgentIdAndUserId(String agentId, String userId) {
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        return getMemoriesByAgentIdAndUserIdAndMemoryType(agentId, userId,null, beginDateTime);
    }


    public LongTermMemory getMemoryByAgentIdAndUserIdAndMemoryType(String agentId, String userId, String memoryType, LocalDateTime beginDateTime) {
        List<LongTermMemory> memories = getMemoriesByAgentIdAndUserIdAndMemoryType(agentId, userId, memoryType, beginDateTime);
        return memories.isEmpty() ? null : memories.get(0);
    }

    public LongTermMemory getMemoryByAgentIdAndUserIdAndMemoryType(String agentId, String userId, LongTermMemoryTypeEnum memoryType) {
        return getMemoryByAgentIdAndUserIdAndMemoryType(agentId, userId, memoryType.getCode(), null);
    } /**
     * @param longTermMemories
     * @return
     */
    public String buildMemoryContent(List<LongTermMemory> longTermMemories){
        return buildMemoryContent(longTermMemories, true);
    }
    /**
     * @param longTermMemories
     * @return
     */
    public String buildMemoryContent(List<LongTermMemory> longTermMemories, Boolean includeDetail){
        StringBuilder memory = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if(!longTermMemories.isEmpty()){
            longTermMemories.stream().map(LongTermMemory::getMemoryType).forEach(memoryType -> {
                longTermMemories.stream().filter(x -> x.getMemoryType().equals(memoryType)).forEach(x -> {
                    memory.append("-------\n")
                            .append(buildMemoryContent(x, includeDetail))
                            .append("\n");
                });
            });
        }
        return memory.toString();
    }


    /**
     * @param memory
     * @return
     */
    public String buildMemoryContent(LongTermMemory memory, Boolean includeDetail){
        if(memory==null){
            return "";
        }
        StringBuilder memorySb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        memorySb.append("【").append(LongTermMemoryTypeEnum.fromCode(memory.getMemoryType()).getName()).append("】\n")
                .append("记忆编号:").append(memory.getId()).append("\n")
                .append("记忆类型:").append(memory.getMemoryType()).append("\n")
                .append("记忆概要:").append(memory.getSummary()).append("\n");
                if(includeDetail!=null && includeDetail) {
                    memorySb.append("记忆内容:").append(memory.getMemory()).append("\n");
                }
        memorySb.append("记忆内容:").append(memory.getMemory()).append("\n")
                .append("更新时间:").append(memory.getUpdateTime().format(formatter)).append("\n")
                .append("创建时间:").append(memory.getCreateTime().format(formatter));
        return memorySb.toString();
    }


    public String getRootMemory(String agentId,String userId) {
        List<LongTermMemory> rootMemories = getRootMemories(agentId,userId);
        StringBuilder sb = new StringBuilder();
        buildMemoryTreeRecursive(sb, rootMemories, 0, false);
        return sb.toString();
    }

    private void buildMemoryTreeRecursive(StringBuilder sb, List<LongTermMemory> memories, int level, boolean includeChildren) {
        for (LongTermMemory memory : memories) {
            for (int i = 0; i < level; i++) {
                sb.append("  ");
            }
            sb.append("- ").append(memory.getMemory()).append(" 编号[").append(memory.getId()).append("]\n");
            if (includeChildren) {

                List<LongTermMemory> children = getChildMemories(memory.getAgentId(), memory.getId());
                if (!children.isEmpty()) {
                    buildMemoryTreeRecursive(sb, children, level + 1, includeChildren);
                }
            }
        }
    }

    @Tool("删除记忆内容，根据指定id删除")
    public String deleteAgentMemory(@P(description="记忆Id") Long id){
        deleteMemory(id);
        return "成功";
    }
    @Tool("保存记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。")
    public String saveMemory(@P(description = "记忆类型:userProfile、taskRecords、expandKnowledge",required = false) String memoryType,
                             @P(description = "记忆概要") String summary,
                             @P(description = "记忆内容") String memory,
                             @P(description = "记忆Id，如果传入记忆Id则为更新，如果不传记忆Id则为新增。",required = false) Long id,
                             InvocationParameters invocationParameters) {
        LongTermMemory memoryEntity =null;
        String memoryHash = UUID.nameUUIDFromBytes((memory).getBytes()).toString();
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        if(id != null){
            // 根据id查询记忆
            memoryEntity = longTermMemoryMapper.findById(id);
            memoryEntity.setAgentId(invocationParametersWrapper.getAgentId());
            memoryEntity.setUserId(invocationParametersWrapper.getUserId());
            memoryEntity.setMemoryType(memoryType);
            memoryEntity.setSummary(summary);
            memoryEntity.setMemory(memory);
            memoryEntity.setMemoryHash(memoryHash);
            memoryEntity.setUpdateTime(LocalDateTime.now());
            longTermMemoryMapper.update(memoryEntity);
        }else{
            memoryEntity = new LongTermMemory();
            memoryEntity.setAgentId(invocationParametersWrapper.getAgentId());
            memoryEntity.setUserId(invocationParametersWrapper.getUserId());
            memoryEntity.setMemoryType(memoryType);
            memoryEntity.setSummary(summary);
            memoryEntity.setMemory(memory);
            memoryEntity.setMemoryHash(memoryHash);
            memoryEntity.setCreateTime(LocalDateTime.now());
            longTermMemoryMapper.insert(memoryEntity);
        }

        return "保存成功：记忆编号为" + memoryEntity.getId();
    }
}
