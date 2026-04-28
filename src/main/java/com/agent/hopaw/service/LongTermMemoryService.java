package com.agent.hopaw.service;

import com.agent.hopaw.mapper.LongTermMemoryMapper;
import com.agent.hopaw.mapper.MemoryProcessLogMapper;
import com.agent.hopaw.model.LongTermMemory;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class LongTermMemoryService {

    public static final String GLOBAL_IDENTITY = "global";

    private final LongTermMemoryMapper longTermMemoryMapper;
    private final MemoryProcessLogMapper memoryProcessLogMapper;

    public LongTermMemoryService(LongTermMemoryMapper longTermMemoryMapper,
                                 MemoryProcessLogMapper memoryProcessLogMapper) {
        this.longTermMemoryMapper = longTermMemoryMapper;
        this.memoryProcessLogMapper = memoryProcessLogMapper;
    }

    public List<LongTermMemory> getMemoriesByIdentity(String identity) {
        return longTermMemoryMapper.findByIdentity(identity);
    }

    public List<LongTermMemory> getRootMemories(String identity) {
        return longTermMemoryMapper.findByIdentityAndParentId(identity, 0L);
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
            sb.append("- ").append(memory.getMemory()).append(" 记忆编号[").append(memory.getId()).append("]\n");
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
        sb.append("【").append(GLOBAL_IDENTITY.equals(identity) ? "全局记忆" : "智能体 " + identity + " 记忆").append("】\n");

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

    public Long getLastProcessedChatId(String identity) {
        return memoryProcessLogMapper.getLastProcessedChatId(identity);
    }

    public void updateLastProcessedChatId(String identity, Long chatId) {
        memoryProcessLogMapper.upsert(identity, chatId);
    }

    @Tool("保存智能体记忆，identity 智能体身份（global是全局，智能体内部记忆是agentId ），memory 记忆内容，parentId 父记忆ID")
    public String saveMemory(String identity, String memory, Long parentId) {
        LongTermMemory memoryEntity = longTermMemoryMapper.findByIdentityAndMemory(identity, memory);
        if (memoryEntity == null) {
            memoryEntity = new LongTermMemory();
            memoryEntity.setIdentity(identity);
            memoryEntity.setMemory(memory);
            memoryEntity.setParentId(parentId);
            memoryEntity.setCreateTime(LocalDateTime.now());
            longTermMemoryMapper.insert(memoryEntity);
        } else {
            memoryEntity.setParentId(parentId);
            memoryEntity.setUpdateTime(LocalDateTime.now());
            longTermMemoryMapper.update(memoryEntity);
        }
        return "记忆保存成功：" + memory + "的编号为" + memoryEntity.getId();
    }
}
