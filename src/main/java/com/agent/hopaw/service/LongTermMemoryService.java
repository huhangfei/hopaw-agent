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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
public class LongTermMemoryService {

    private final LongTermMemoryMapper longTermMemoryMapper;
    private final SysConfigService sysConfigService;

    public LongTermMemoryService(LongTermMemoryMapper longTermMemoryMapper, SysConfigService sysConfigService) {
        this.longTermMemoryMapper = longTermMemoryMapper;
        this.sysConfigService = sysConfigService;
    }

    public List<LongTermMemory> getChildMemories(Long parentId) {
        return longTermMemoryMapper.findByParentId(parentId);
    }

    public LongTermMemory getMemoryById(Long id) {
        return longTermMemoryMapper.findById(id);
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

    public void deleteMemory(Long id) {
        longTermMemoryMapper.deleteById(id);
    }

    public void update(LongTermMemory entity) {
        entity.setMemoryHash(String.valueOf(entity.getMemory().hashCode()));
        longTermMemoryMapper.update(entity);
    }

    public void moveMemory(Long id, Long newParentId) {
        longTermMemoryMapper.updateParentId(id, newParentId);
    }

    public List<LongTermMemory> getAllMemoriesByAgentId(Long agentId, String userId) {
        return longTermMemoryMapper.findByAgentIdAndUserId(agentId, userId);
    }

    public List<LongTermMemory> getMemoriesByAgentIdAndUserIdAndMemoryType(Long agentId, String userId, String memoryType, LocalDateTime beginDateTime) {
        return longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndTime(agentId, userId, memoryType, beginDateTime);
    }


    /**
     * 获取近期活跃所有类型记忆
     *
     * @param agentId
     * @param userId
     * @return
     */
    public List<LongTermMemory> getRecentActivityMemoriesByAgentIdAndUserId(Long agentId, String userId) {
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        return getMemoriesByAgentIdAndUserIdAndMemoryType(agentId, userId, null, beginDateTime);
    }

    /**
     * 获取近期活跃所有类型记忆
     *
     * @param agentId
     * @param userId
     * @return
     */
    public List<LongTermMemory> getRecentActivityMemories(Long agentId,String userId) {
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        List<LongTermMemory> result=new ArrayList<>();
        List<LongTermMemory> userMemories = longTermMemoryMapper.getRecentActivityMemoriesByUserIdAndTypesAndTime(null,userId, Arrays.asList(LongTermMemoryTypeEnum.USER_PROFILE), null);
        result.addAll(userMemories);
        List<LongTermMemory> agentAndUserMemories = longTermMemoryMapper.getRecentActivityMemoriesByUserIdAndTypesAndTime(agentId,userId, Arrays.asList(LongTermMemoryTypeEnum.TASK_RECORDS), beginDateTime);
        result.addAll(agentAndUserMemories);
        return result;
    }
    /**
     * 获取近期活跃所有类型记忆
     *
     * @param agentId
     * @param userId
     * @return
     */
    public List<LongTermMemory> getRecentActivityAllMemories(Long agentId,String userId) {
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        List<LongTermMemory> result=new ArrayList<>();
        List<LongTermMemory> userMemories = longTermMemoryMapper.getRecentActivityMemoriesByUserIdAndTypesAndTime(null,userId, Arrays.asList(LongTermMemoryTypeEnum.USER_PROFILE,LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE), null);
        result.addAll(userMemories);
        List<LongTermMemory> agentAndUserMemories = longTermMemoryMapper.getRecentActivityMemoriesByUserIdAndTypesAndTime(agentId,userId, Arrays.asList(LongTermMemoryTypeEnum.TASK_RECORDS), beginDateTime);
        result.addAll(agentAndUserMemories);
        return result;
    }


    public LongTermMemory getMemoryByAgentIdAndUserIdAndMemoryType(Long agentId, String userId, String memoryType, LocalDateTime beginDateTime) {
        List<LongTermMemory> memories = getMemoriesByAgentIdAndUserIdAndMemoryType(agentId, userId, memoryType, beginDateTime);
        return memories.isEmpty() ? null : memories.get(0);
    }

    public LongTermMemory getMemoryByAgentIdAndUserIdAndMemoryType(Long agentId, String userId, LongTermMemoryTypeEnum memoryType) {
        return getMemoryByAgentIdAndUserIdAndMemoryType(agentId, userId, memoryType.getCode(), null);
    }

    /**
     * 查询用户所有类型记忆
     * @param agentId
     * @param userId
     * @return
     */
    public List<LongTermMemory> queryUserAllMemories(Long agentId, String userId){
        //用户画像
        List<LongTermMemory> userProfile = longTermMemoryMapper.findByUserIdAndMemoryType(userId, LongTermMemoryTypeEnum.USER_PROFILE.getCode());
        //任务记录
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        List<LongTermMemory> taskRecords = longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndTime(agentId, userId, LongTermMemoryTypeEnum.TASK_RECORDS.getCode(), beginDateTime);
        //扩展知识
        List<LongTermMemory> expandKnowledge = longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndTime(agentId, userId, LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE.getCode(), null);
        List<LongTermMemory> result=new ArrayList<>();
        result.addAll(userProfile);
        result.addAll(taskRecords);
        result.addAll(expandKnowledge);
        return result;
    }
    /**
     * 查询用户所有类型记忆
     * @param agentId
     * @param userId
     * @return
     */
    public String queryUserAllMemoriesContent(Long agentId, String userId, Function<LongTermMemory, Boolean> includeDetailFun){
        List<LongTermMemory> memories = queryUserAllMemories(agentId, userId);
        String memoryContent = buildMemoryContent(memories, includeDetailFun);
        return memoryContent;
    }

    /**
     * 查询用户画像记忆
     * @param userId
     * @return
     */
    public String queryUserProfileMemoryContent(String userId){
        List<LongTermMemory> memories = longTermMemoryMapper.findByUserIdAndMemoryType(userId, LongTermMemoryTypeEnum.USER_PROFILE.getCode());
        if(memories.isEmpty()){
            return "";
        }
        return buildMemoryContent(memories,true);
    }

    /**
     * @param longTermMemories
     * @return
     */
    public String buildMemoryContent(List<LongTermMemory> longTermMemories) {
        return buildMemoryContent(longTermMemories, true);
    }

    /**
     * @param longTermMemories
     * @return
     */
    public String buildMemoryContent(List<LongTermMemory> longTermMemories, Boolean includeDetail) {
        StringBuilder memory = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (!longTermMemories.isEmpty()) {
            longTermMemories.stream().map(LongTermMemory::getMemoryType).distinct().forEach(memoryType -> {
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
     * @param longTermMemories
     * @return
     */
    public String buildMemoryContent(List<LongTermMemory> longTermMemories, Function<LongTermMemory, Boolean> includeDetailFun) {
        StringBuilder memory = new StringBuilder();
        if (!longTermMemories.isEmpty()) {
            longTermMemories.stream().map(LongTermMemory::getMemoryType).distinct().forEach(memoryType -> {
                longTermMemories.stream().filter(x -> x.getMemoryType().equals(memoryType)).forEach(x -> {
                    memory.append("-------\n")
                            .append(buildMemoryContent(x, includeDetailFun))
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
    public String buildMemoryContent(LongTermMemory memory, Boolean includeDetail) {
        if (memory == null) {
            return "";
        }
        StringBuilder memorySb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LongTermMemoryTypeEnum longTermMemoryTypeEnum = LongTermMemoryTypeEnum.fromCode(memory.getMemoryType());
        memorySb.append("【").append(longTermMemoryTypeEnum != null ? longTermMemoryTypeEnum.getName() : memory.getMemoryType()).append("】\n")
                .append("编号:").append(memory.getId()).append("\n")
                .append("类型:").append(memory.getMemoryType()).append("\n")
                .append("概要:").append(memory.getSummary()).append("\n");
        if (memory.getMemory() != null && !memory.getMemory().isEmpty()) {
            if (includeDetail != null && includeDetail) {
                memorySb.append("内容:").append(memory.getMemory()).append("\n");
            } else {
                memorySb.append("内容:").append("请根据记忆编号调用Tool查看详细内容").append("\n");
            }
        }
//        memorySb.append("更新时间:").append(memory.getUpdateTime().format(formatter)).append("\n")
//                .append("创建时间:").append(memory.getCreateTime().format(formatter));
        return memorySb.toString();
    }

    public String buildMemoryContent(LongTermMemory memory, Function<LongTermMemory, Boolean> includeDetailFun) {
        Boolean includeDetail = includeDetailFun.apply(memory);
        return buildMemoryContent(memory, includeDetail);
    }

    @Tool("查询用户记忆详细内容,根据指定Id查询")
    public String getMemoryContentById(@P(description="记忆Id")Long id) {
        LongTermMemory memory = getMemoryById(id);
        String memoryContent = buildMemoryContent(memory, true);
        if(memory!=null){
            List<LongTermMemory> childMemories = getChildMemories(memory.getId());
            if(childMemories.isEmpty()){
                memoryContent+="\n子记忆:\n"+buildMemoryContent(childMemories,false);
            }
        }
        return memoryContent;
    }
    @Tool("删除记忆内容，根据指定id删除")
    public String deleteAgentMemory(@P(description = "记忆Id") Long id) {
        deleteMemory(id);
        return "成功";
    }

    @Tool("保存记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。")
    public String saveMemory(@P(description = "记忆类型:userProfile、taskRecords、expandKnowledge", required = false) String memoryType,
                             @P(description = "记忆概要") String summary,
                             @P(description = "记忆内容") String memory,
                             @P(description = "记忆Id，如果传入记忆Id则为更新，如果不传记忆Id则为新增。", required = false) Long id,
                             InvocationParameters invocationParameters) {
        LongTermMemory memoryEntity = null;
        String memoryHash = UUID.nameUUIDFromBytes((memory).getBytes()).toString();
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);

        if (id != null) {
            // 根据id查询记忆
            memoryEntity = longTermMemoryMapper.findById(id);
            memoryEntity.setAgentId(String.valueOf(invocationParametersWrapper.getAgentId()));
            memoryEntity.setUserId(invocationParametersWrapper.getUserId());
            memoryEntity.setMemoryType(memoryType);
            memoryEntity.setSummary(summary);
            memoryEntity.setMemory(memory);
            memoryEntity.setMemoryHash(memoryHash);
            memoryEntity.setUpdateTime(LocalDateTime.now());
            longTermMemoryMapper.update(memoryEntity);
        } else {
            memoryEntity = new LongTermMemory();
            memoryEntity.setAgentId(String.valueOf(invocationParametersWrapper.getAgentId()));
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
