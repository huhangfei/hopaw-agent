package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.infra.mapper.LongTermMemoryMapper;
import com.agent.hopaw.infra.service.SysConfigService;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
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
public class LongTermMemoryService implements ILongTermMemoryService {

    private final LongTermMemoryMapper longTermMemoryMapper;
    private final SysConfigService sysConfigService;

    public LongTermMemoryService(LongTermMemoryMapper longTermMemoryMapper, SysConfigService sysConfigService) {
        this.longTermMemoryMapper = longTermMemoryMapper;
        this.sysConfigService = sysConfigService;
    }

    @Override
    public List<LongTermMemory> getChildMemories(Long parentId) {
        return longTermMemoryMapper.findByParentId(parentId);
    }

    @Override
    public LongTermMemory getMemoryById(Long id) {
        return longTermMemoryMapper.findById(id);
    }


    @Override
    public LongTermMemory createMemory(String sessionId, String memory, Long parentId, String userId,
                                       String memoryType, String summary) {
        LongTermMemory entity = new LongTermMemory(sessionId,userId,memoryType,summary,memory,parentId);
        longTermMemoryMapper.insert(entity);
        return entity;
    }

    @Override
    public void deleteMemory(Long id) {
        longTermMemoryMapper.deleteById(id);
    }

    @Override
    public void update(LongTermMemory entity) {
        entity.setMemoryHash(String.valueOf(entity.getMemory().hashCode()));
        longTermMemoryMapper.update(entity);
    }

    @Override
    public void moveMemory(Long id, Long newParentId) {
        longTermMemoryMapper.updateParentId(id, newParentId);
    }

    @Override
    public List<LongTermMemory> getAllMemoriesByAgentId(String sessionId, String userId) {
        //用户画像
        List<LongTermMemory> userProfile = longTermMemoryMapper.findByUserIdAndMemoryType(userId, LongTermMemoryTypeEnum.USER_PROFILE.getCode());
        //任务记录
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        List<LongTermMemory> taskRecords = longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndTime(sessionId, userId, LongTermMemoryTypeEnum.TASK_RECORDS.getCode(), beginDateTime);
        //扩展知识
        List<LongTermMemory> expandKnowledge = longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndTime(sessionId, userId, LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE.getCode(), null);
        List<LongTermMemory> result=new ArrayList<>();
        result.addAll(userProfile);
        result.addAll(taskRecords);
        result.addAll(expandKnowledge);
        return result;
    }

    @Override
    public void deleteExpiredTaskRecordsMemories(String sessionId, String userId) {
        int taskRecordsClearTimeoutDay = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsClearTimeoutDay", "7"));
        LocalDateTime endDateTime = LocalDate.now().atStartOfDay().minusDays(taskRecordsClearTimeoutDay);
        longTermMemoryMapper.deleteBySessionIdAndUserIdAndMemoryTypeAndEndDateTime(sessionId, userId, LongTermMemoryTypeEnum.TASK_RECORDS.getCode(),endDateTime);
    }

    @Override
    public List<LongTermMemory> findExpiredTaskRecordsMemories(String sessionId, String userId) {
        int taskRecordsClearTimeoutDay = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsClearTimeoutDay", "7"));
        LocalDateTime endDateTime = LocalDate.now().atStartOfDay().minusDays(taskRecordsClearTimeoutDay);
        return longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndEndDateTime(sessionId, userId,LongTermMemoryTypeEnum.TASK_RECORDS.getCode(),endDateTime);
    }

    /**
     * 查询用户所有类型记忆
     * @param sessionId
     * @param userId
     * @return
     */
    @Override
    public List<LongTermMemory> queryUserAllMemories(String sessionId, String userId){
        //用户画像
        List<LongTermMemory> userProfile = longTermMemoryMapper.findByUserIdAndMemoryType(userId, LongTermMemoryTypeEnum.USER_PROFILE.getCode());
        //任务记录
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        List<LongTermMemory> taskRecords = longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndTime(sessionId, userId, LongTermMemoryTypeEnum.TASK_RECORDS.getCode(), beginDateTime);
        //扩展知识
        List<LongTermMemory> expandKnowledge = longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndTime(sessionId, userId, LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE.getCode(), null);
        List<LongTermMemory> result=new ArrayList<>();
        result.addAll(userProfile);
        result.addAll(taskRecords);
        result.addAll(expandKnowledge);
        return result;
    }
    /**
     * 查询用户所有类型记忆
     * @param sessionId
     * @param userId
     * @param includeDetailFun
     * @return
     */
    @Override
    public String queryUserAllMemoriesContent(String sessionId, String userId, Function<LongTermMemory, Boolean> includeDetailFun){
        List<LongTermMemory> memories = queryUserAllMemories(sessionId, userId);
        String memoryContent = buildMemoryContent(memories, includeDetailFun);
        return memoryContent;
    }

    /**
     * 查询用户画像记忆
     * @param userId
     * @return
     */
    @Override
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
    @Override
    public List<LongTermMemory> queryUserTaskRecordsMemory(String sessionId, String userId){
        int taskRecordsArrangeTimeoutHour = Integer.parseInt(sysConfigService.getValueByKey("taskRecordsArrangeTimeoutHour", "48"));
        LocalDateTime beginDateTime = LocalDateTime.now().minusHours(taskRecordsArrangeTimeoutHour);
        List<LongTermMemory> taskRecords = longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndTime(sessionId, userId, LongTermMemoryTypeEnum.TASK_RECORDS.getCode(), beginDateTime);
        return taskRecords;
    }
    /**
     * 查询用户任务记录记忆
     * @param sessionId
     * @param userId
     * @return
     */
    @Override
    public String queryUserTaskRecordsMemoryContent(String sessionId, String userId, Boolean includeDetail){
        List<LongTermMemory> taskRecords = queryUserTaskRecordsMemory(sessionId, userId);
        return buildMemoryContent(taskRecords,includeDetail);
    }
    /**
     * 查询用户扩展知识记忆
     * @param userId
     * @return
     */
    @Override
    public List<LongTermMemory> queryUserExpandKnowledgeMemory(String sessionId, String userId){
        List<LongTermMemory> expandKnowledge = longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndTime(sessionId, userId, LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE.getCode(), null);
        return expandKnowledge;
    }
    /**
     * 查询用户扩展知识记忆
     * @param sessionId
     * @param userId
     * @param includeDetail
     * @return
     */
    @Override
    public String queryUserExpandKnowledgeMemoryContent(String sessionId, String userId, Boolean includeDetail){
        List<LongTermMemory> expandKnowledge = longTermMemoryMapper.findBySessionIdAndUserIdAndMemoryTypeAndTime(sessionId, userId, LongTermMemoryTypeEnum.EXPAND_KNOWLEDGE.getCode(), null);
        return buildMemoryContent(expandKnowledge,includeDetail);
    }


    /**
     * @param longTermMemories
     * @return
     */
    @Override
    public String buildMemoryContent(List<LongTermMemory> longTermMemories) {
        return buildMemoryContent(longTermMemories, true);
    }

    /**
     * @param longTermMemories
     * @return
     */
    @Override
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
    @Override
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
    @Override
    public String buildMemoryContent(LongTermMemory memory, Boolean includeDetail) {
        return buildMemoryContent(memory, includeDetail, true);
    }

        /**
         * @param memory
         * @return
         */
    @Override
    public String buildMemoryContent(LongTermMemory memory, Boolean includeDetail, Boolean includeType) {
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

    @Override
    public String buildMemoryContent(LongTermMemory memory, Function<LongTermMemory, Boolean> includeDetailFun, Boolean includeType) {
        Boolean includeDetail = includeDetailFun.apply(memory);
        return buildMemoryContent(memory, includeDetail,includeType);
    }
    @Override
    public String buildMemoryContent(LongTermMemory memory, Function<LongTermMemory, Boolean> includeDetailFun) {
        return buildMemoryContent(memory, includeDetailFun,false);
    }

    @Override
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

    @Override
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
            memoryEntity.setSessionId(invocationParametersWrapper.getSessionId());
            memoryEntity.setUserId(invocationParametersWrapper.getUserId());
            memoryEntity.setMemoryType(memoryType);
            memoryEntity.setSummary(summary);
            memoryEntity.setMemory(memory);
            memoryEntity.setMemoryHash(memoryHash);
            memoryEntity.setUpdateTime(LocalDateTime.now());
            longTermMemoryMapper.update(memoryEntity);
        } else {
            memoryEntity = new LongTermMemory();
            memoryEntity.setSessionId(invocationParametersWrapper.getSessionId());
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
    @Override
    public String getMemoryOrganizingRules() {
        return sysConfigService.getValueByKey("memory_prompt", "");
    }
}
