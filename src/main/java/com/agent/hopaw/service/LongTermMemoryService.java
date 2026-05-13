package com.agent.hopaw.service;

import com.agent.hopaw.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.mapper.LongTermMemoryMapper;
import com.agent.hopaw.model.LongTermMemory;
import com.agent.hopaw.util.InvocationParametersWrapper;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public void deleteExpiredTaskRecordsMemories(Long agentId, String userId) {
        int taskRecordsClearTimeoutDay = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsClearTimeoutDay", "7"));
        LocalDateTime endDateTime = LocalDate.now().atStartOfDay().minusDays(taskRecordsClearTimeoutDay);
        longTermMemoryMapper.deleteByAgentIdAndUserIdAndMemoryTypeAndEndDateTime(agentId, userId, LongTermMemoryTypeEnum.TASK_RECORDS.getCode(),endDateTime);
    }

    public List<LongTermMemory> findExpiredTaskRecordsMemories(Long agentId, String userId) {
        int taskRecordsClearTimeoutDay = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsClearTimeoutDay", "7"));
        LocalDateTime endDateTime = LocalDate.now().atStartOfDay().minusDays(taskRecordsClearTimeoutDay);
        return longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndEndDateTime(agentId, userId,LongTermMemoryTypeEnum.TASK_RECORDS.getCode(),endDateTime);
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
     * 查询用户任务记录记忆
     * @param userId
     * @return
     */
    public List<LongTermMemory> queryUserTaskRecordsMemory(Long agentId,String userId){
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        List<LongTermMemory> taskRecords = longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndTime(agentId, userId, LongTermMemoryTypeEnum.TASK_RECORDS.getCode(), beginDateTime);
        return taskRecords;
    }
    /**
     * 查询用户任务记录记忆
     * @param userId
     * @return
     */
    public String queryUserTaskRecordsMemoryContent(Long agentId,String userId,Boolean includeDetail){
        List<LongTermMemory> taskRecords = queryUserTaskRecordsMemory(agentId, userId);
        return buildMemoryContent(taskRecords,includeDetail);
    }
    /**
     * 查询用户扩展知识记忆
     * @param userId
     * @return
     */
    public List<LongTermMemory> queryUserExpandKnowledgeMemory(Long agentId,String userId){
        List<LongTermMemory> expandKnowledge = longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndTime(agentId, userId, LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE.getCode(), null);
        return expandKnowledge;
    }
    /**
     * 查询用户扩展知识记忆
     * @param userId
     * @return
     */
    public String queryUserExpandKnowledgeMemoryContent(Long agentId,String userId,Boolean includeDetail){
        List<LongTermMemory> expandKnowledge = longTermMemoryMapper.findByAgentIdAndUserIdAndMemoryTypeAndTime(agentId, userId, LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE.getCode(), null);
        return buildMemoryContent(expandKnowledge,includeDetail);
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
                LongTermMemoryTypeEnum longTermMemoryTypeEnum = LongTermMemoryTypeEnum.fromCode(memoryType);
                memory.append("----").append(longTermMemoryTypeEnum != null ? longTermMemoryTypeEnum.getName() : memoryType).append("("+memoryType+")").append("----\n");
                longTermMemories.stream().filter(x -> x.getMemoryType().equals(memoryType)).forEach(x -> {
                    memory.append(buildMemoryContent(x, includeDetail,false));
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
                LongTermMemoryTypeEnum longTermMemoryTypeEnum = LongTermMemoryTypeEnum.fromCode(memoryType);
                memory.append("----").append(longTermMemoryTypeEnum != null ? longTermMemoryTypeEnum.getName() : memoryType).append("("+memoryType+")").append("----\n");
                longTermMemories.stream().filter(x -> x.getMemoryType().equals(memoryType)).forEach(x -> {
                    memory.append(buildMemoryContent(x, includeDetailFun,false));
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
        return buildMemoryContent(memory, includeDetail, true);
    }

        /**
         * @param memory
         * @return
         */
    public String buildMemoryContent(LongTermMemory memory, Boolean includeDetail,Boolean includeType) {
        if (memory == null) {
            return "";
        }
        StringBuilder memorySb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LongTermMemoryTypeEnum longTermMemoryTypeEnum = LongTermMemoryTypeEnum.fromCode(memory.getMemoryType());
        if(includeType!=null && includeType){
            memorySb.append("【").append(longTermMemoryTypeEnum != null ? longTermMemoryTypeEnum.getName() : memory.getMemoryType()).append("】").append("(").append(memory.getMemoryType()).append(")\n");
        }
        memorySb.append("编号:").append(memory.getId()).append("\n")
                .append("概要:").append(memory.getSummary()).append("\n");
        if (memory.getMemory() != null && !memory.getMemory().isEmpty()) {
            if (includeDetail != null && includeDetail) {
                memorySb.append("内容:").append(memory.getMemory()).append("\n");
            } else {
                memorySb.append("内容:").append("已遮挡，调用Tool查看详情").append("\n");
            }
        }
//        memorySb.append("更新时间:").append(memory.getUpdateTime().format(formatter)).append("\n")
//                .append("创建时间:").append(memory.getCreateTime().format(formatter));
        return memorySb.append("----------------\n").toString();
    }

    public String buildMemoryContent(LongTermMemory memory, Function<LongTermMemory, Boolean> includeDetailFun,Boolean includeType) {
        Boolean includeDetail = includeDetailFun.apply(memory);
        return buildMemoryContent(memory, includeDetail,includeType);
    }
    public String buildMemoryContent(LongTermMemory memory, Function<LongTermMemory, Boolean> includeDetailFun ) {
        return buildMemoryContent(memory, includeDetailFun,false);
    }

    public String getMemoryContentById(Long id) {
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

    public String saveMemory(String memoryType,
                             String summary,
                             String memory,
                             Long id,
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
    public String getMemoryOrganizingRules() {
        return sysConfigService.getValueByKey("memory_prompt", "");
    }
}
