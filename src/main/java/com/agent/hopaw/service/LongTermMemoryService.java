package com.agent.hopaw.service;

import com.agent.hopaw.mapper.LongTermMemoryMapper;
import com.agent.hopaw.model.LongTermMemory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class LongTermMemoryService {

    private final LongTermMemoryMapper longTermMemoryMapper;

    public LongTermMemoryService(LongTermMemoryMapper longTermMemoryMapper) {
        this.longTermMemoryMapper = longTermMemoryMapper;
    }

    public List<LongTermMemory> getMemoriesByIdentity(String identity) {
        return longTermMemoryMapper.findByIdentity(identity);
    }

    public List<LongTermMemory> getRootMemories(String identity) {
        return longTermMemoryMapper.findRootsByIdentity(identity);
    }

    public List<LongTermMemory> getChildMemories(String identity, Long parentId) {
        return longTermMemoryMapper.findByIdentityAndParentId(identity, parentId);
    }

    public LongTermMemory getMemoryById(Long id) {
        return longTermMemoryMapper.findById(id);
    }

    public LongTermMemory createMemory(String identity, String memory, Long parentId) {
        LongTermMemory entity = new LongTermMemory(identity, memory, parentId);
        longTermMemoryMapper.insert(entity);
        return entity;
    }

    public LongTermMemory createRootMemory(String identity, String memory) {
        return createMemory(identity, memory, null);
    }

    public LongTermMemory createChildMemory(String identity, String memory, Long parentId) {
        return createMemory(identity, memory, parentId);
    }

    public void updateMemory(Long id, String memory, Long parentId) {
        LongTermMemory existing = longTermMemoryMapper.findById(id);
        if (existing != null) {
            existing.setMemory(memory);
            existing.setParentId(parentId);
            longTermMemoryMapper.update(existing);
        }
    }

    public void updateMemoryContent(Long id, String memory) {
        LongTermMemory existing = longTermMemoryMapper.findById(id);
        if (existing != null) {
            existing.setMemory(memory);
            longTermMemoryMapper.update(existing);
        }
    }

    public void deleteMemory(Long id) {
        longTermMemoryMapper.deleteById(id);
    }

    public void deleteAllMemories(String identity) {
        longTermMemoryMapper.deleteByIdentity(identity);
    }

    public String getMemoryTree(String identity) {
        List<LongTermMemory> rootMemories = getRootMemories(identity);
        StringBuilder sb = new StringBuilder();
        buildMemoryTreeRecursive(sb, rootMemories, 0, true);
        return sb.toString();
    }

    public String getMemoryTree(String identity, Long parentId) {
        LongTermMemory rootMemory = getMemoryById(parentId);
        if (rootMemory == null) {
            return "未找到ID为 " + parentId + " 的记忆";
        }
        StringBuilder sb = new StringBuilder();
        buildMemoryTreeRecursive(sb, Arrays.asList(rootMemory), 0, true);
        return sb.toString();
    }

    public String getRootMemory(String identity) {
        List<LongTermMemory> rootMemories = getRootMemories(identity);
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

                List<LongTermMemory> children = getChildMemories(memory.getIdentity(), memory.getId());
                if (!children.isEmpty()) {
                    buildMemoryTreeRecursive(sb, children, level + 1, includeChildren);
                }
            }
        }
    }

    public String getMemorySummary(String identity) {
        List<LongTermMemory> memories = getMemoriesByIdentity(identity);
        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【").append("智能体 " + identity + " 记忆").append("】\n");

        List<LongTermMemory> rootMemories = getRootMemories(identity);
        for (LongTermMemory root : rootMemories) {
            sb.append(root.getMemory());
            List<LongTermMemory> children = getChildMemories(identity, root.getId());
            if (!children.isEmpty()) {
                sb.append("：");
                for (int i = 0; i < children.size(); i++) {
                    sb.append(children.get(i).getMemory());
                    if (i < children.size() - 1) {
                        sb.append("，");
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    @Tool("删除智能体记忆内容，此方法删除指定id的记忆内容。")
    public String deleteAgentMemory(@P(description="记忆ID") Long id){
        deleteMemory(id);
        return "成功";
    }
    @Tool("保存智能体记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。")
    public String saveMemory(@P(description = "智能体标识,传入agentId") String identity,
                             @P(description = "记忆内容") String memory,
                             @P(description = "父记忆ID",required = false) Long parentId,
                             @P(description = "记忆Id",required = false) Long id) {
        LongTermMemory memoryEntity =null;
        String memoryHash = UUID.nameUUIDFromBytes((memory).getBytes()).toString();
        if(id != null){
            // 根据id查询记忆
            memoryEntity = longTermMemoryMapper.findById(id);
            memoryEntity.setIdentity(identity);
            memoryEntity.setMemory(memory);
            memoryEntity.setMemoryHash(memoryHash);
            memoryEntity.setParentId(parentId);
            memoryEntity.setUpdateTime(LocalDateTime.now());
            longTermMemoryMapper.update(memoryEntity);
        }else{
            memoryEntity = new LongTermMemory();
            memoryEntity.setIdentity(identity);
            memoryEntity.setMemory(memory);
            memoryEntity.setMemoryHash(memoryHash);
            memoryEntity.setParentId(parentId);
            memoryEntity.setCreateTime(LocalDateTime.now());
            longTermMemoryMapper.insert(memoryEntity);
        }

        return "保存成功：记忆编号为" + memoryEntity.getId();
    }
}
